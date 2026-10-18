/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.share

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.hasValidRemoteData
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ShareFilesTest {

    private val fileSystem = FileSystem.SYSTEM
    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "share-files-${Uuid.random()}"
    private val temporary = root / "tmp"

    @AfterTest
    fun cleanUp() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun uploadsArePrivatePersistentAndHashedOverTheCiphertext() = runTest {
        val plain = Random.nextBytes(300_000)
        val file = write("photo.jpg", plain)
        val key = generateRandomAES256Key()
        val api = RecordingAssetApi()
        val progress = mutableListOf<Pair<Long, Long>>()

        val result = uploads(api, auditLog = false, key = key).upload(listOf(file), CONVERSATION) { sent, total ->
            progress += sent to total
        }

        val uploaded = assertIs<Either.Right<List<UploadedFile>>>(result).value.single()
        val body = api.bodies.single()
        val metadata = api.metadata.single()
        assertContentEquals(plain, decryptDataWithAES256(EncryptedData(body), key).data)
        assertContentEquals(calcSHA256(body), uploaded.sha256.data)
        assertEquals(calcMd5(body), metadata.md5)
        assertEquals(body.size.toLong(), uploaded.encryptedSize)
        assertEquals(UploadedAssetId("asset-1", "wire.com", "token-1"), uploaded.assetId)
        assertEquals(false, metadata.public)
        assertEquals(AssetRetentionType.PERSISTENT, metadata.retentionType)
        assertEquals("image/jpeg", metadata.mimeType)
        assertNull(metadata.conversationId)
        assertNull(metadata.filename)
        assertEquals(plain.size.toLong() to plain.size.toLong(), progress.last())
        assertEquals(progress, progress.sortedBy { it.first })
        // Reading the last byte is not uploading it: only the backend's answer completes the file.
        assertTrue(progress.dropLast(1).all { (sent, total) -> sent < total })
        assertTrue(fileSystem.list(temporary).isEmpty(), "The encrypted copy is deleted")
    }

    @Test
    fun anAuditingBackendLearnsTheConversationTheFileAndItsType() = runTest {
        val api = RecordingAssetApi()

        uploads(api, auditLog = true).upload(listOf(write("notes.pdf", byteArrayOf(1, 2, 3), "application/pdf")), CONVERSATION) { _, _ -> }

        val metadata = api.metadata.single()
        assertEquals(QualifiedID("conversation", "wire.com"), metadata.conversationId)
        assertEquals("notes.pdf", metadata.filename)
        assertEquals("application/pdf", metadata.filetype)
    }

    @Test
    fun theFirstFailedUploadEndsTheShareAndLeavesNoEncryptedCopy() = runTest {
        val offline = RecordingAssetApi(failWith = KaliumException.NoNetwork())
        val files = listOf(write("one.jpg", byteArrayOf(1)), write("two.jpg", byteArrayOf(2)))

        val result = uploads(offline).upload(files, CONVERSATION) { _, _ -> }

        assertEquals(ShareOutcome(ShareStatus.OFFLINE, "UPLOAD_FAILED"), assertIs<Either.Left<ShareOutcome>>(result).value)
        assertEquals(1, offline.attempts, "The second file is not uploaded")
        assertTrue(fileSystem.list(temporary).isEmpty())

        val failing = RecordingAssetApi(failWith = KaliumException.GenericError(IllegalStateException()))
        val failed = uploads(failing).upload(files, CONVERSATION) { _, _ -> }
        assertEquals(ShareStatus.FAILED, assertIs<Either.Left<ShareOutcome>>(failed).value.status)
    }

    @Test
    fun anUnreadableFileFailsWithoutUploading() = runTest {
        val api = RecordingAssetApi()
        val missing = ShareFile((root / "missing.jpg").toString(), "missing.jpg", "image/jpeg", sizeInBytes = 1)

        val result = uploads(api).upload(listOf(missing), CONVERSATION) { _, _ -> }

        assertEquals(ShareOutcome(ShareStatus.FAILED, "UPLOAD_FAILED"), assertIs<Either.Left<ShareOutcome>>(result).value)
        assertEquals(0, api.attempts)
        assertTrue(fileSystem.list(temporary).isEmpty())
    }

    @Test
    fun theAccountsFileRulesRefuseWhatTheAppRefuses() {
        val personal = SharePolicy(allowedExtensions = null, fileSizeLimitBytes = SharePolicy.PERSONAL_SIZE_LIMIT_BYTES)
        val restricted = SharePolicy(allowedExtensions = listOf("pdf", "jpg"), fileSizeLimitBytes = SharePolicy.TEAM_SIZE_LIMIT_BYTES)

        assertEquals(FileRefusal.FILE_SHARING_DISABLED, (null as SharePolicy?).refusal("a.pdf", "application/pdf", 1))
        assertNull(personal.refusal("a.pdf", "application/pdf", SharePolicy.PERSONAL_SIZE_LIMIT_BYTES))
        assertEquals(FileRefusal.FILE_TOO_LARGE, personal.refusal("a.pdf", "application/pdf", SharePolicy.PERSONAL_SIZE_LIMIT_BYTES + 1))
        // Images Wire displays have their own limit, whatever the team; other images count as files.
        assertEquals(FileRefusal.FILE_TOO_LARGE, restricted.refusal("a.jpg", "image/jpeg", SharePolicy.IMAGE_SIZE_LIMIT_BYTES + 1))
        assertNull(restricted.refusal("a.pdf", "application/pdf", SharePolicy.IMAGE_SIZE_LIMIT_BYTES + 1))
        // As in the app, the extension must match the list, case included.
        assertEquals(FileRefusal.FILE_TYPE_RESTRICTED, restricted.refusal("a.zip", "application/zip", 1))
        assertEquals(FileRefusal.FILE_TYPE_RESTRICTED, restricted.refusal("a.PDF", "application/pdf", 1))
        assertNull(restricted.refusal("a.jpg", "image/jpeg", 1))
    }

    @Test
    fun theAccountsFileRulesFollowItsTeamAndFileSharingSetting() = runTest {
        withDatabase { database ->
            val config = UserConfigDataSource(database.userPrefsDAO, database.userConfigDAO, lazy { BuildFileRestrictionState.NoRestriction })

            assertNull(database.sharePolicy(SELF, BuildFileRestrictionState.NoRestriction), "An unread setting counts as disabled")

            config.setFileSharingStatus(status = true, isStatusChanged = false)
            assertEquals(
                SharePolicy(null, SharePolicy.PERSONAL_SIZE_LIMIT_BYTES),
                database.sharePolicy(SELF, BuildFileRestrictionState.NoRestriction)
            )
            assertEquals(
                SharePolicy(listOf("pdf"), SharePolicy.PERSONAL_SIZE_LIMIT_BYTES),
                database.sharePolicy(SELF, BuildFileRestrictionState.AllowSome(listOf("pdf")))
            )

            database.userDAO.upsertUsers(listOf(self(team = "team")))
            assertEquals(SharePolicy.TEAM_SIZE_LIMIT_BYTES, database.sharePolicy(SELF, BuildFileRestrictionState.NoRestriction)?.fileSizeLimitBytes)

            config.setFileSharingStatus(status = false, isStatusChanged = false)
            assertNull(database.sharePolicy(SELF, BuildFileRestrictionState.NoRestriction))
        }
    }

    @Test
    fun uploadedFilesBecomeTheAssetsTheAppSends() {
        fun content(mimeType: String, width: Int? = null, height: Int? = null, durationMs: Long? = null) = UploadedFile(
            file = ShareFile("/tmp/file", "file", mimeType, sizeInBytes = 42, width = width, height = height, durationMs = durationMs),
            otrKey = AES256Key(byteArrayOf(1)),
            sha256 = SHA256Key(byteArrayOf(2)),
            assetId = UploadedAssetId("key", "wire.com", "token"),
            encryptedSize = 64,
        ).content().value

        val image = content("image/jpeg", width = 1448, height = 1086)
        assertEquals(AssetContent.AssetMetadata.Image(1448, 1086), image.metadata)
        assertEquals(42, image.sizeInBytes)
        assertEquals("file", image.name)
        assertEquals("key", image.remoteData.assetId)
        assertEquals("wire.com", image.remoteData.assetDomain)
        assertEquals("token", image.remoteData.assetToken)
        assertContentEquals(byteArrayOf(1), image.remoteData.otrKey)
        assertContentEquals(byteArrayOf(2), image.remoteData.sha256)
        assertEquals(MessageEncryptionAlgorithm.AES_CBC, image.remoteData.encryptionAlgorithm)
        assertTrue(image.hasValidRemoteData())

        // Like the app: an image without dimensions, or one Wire does not display, goes as a file.
        assertNull(content("image/jpeg").metadata)
        assertNull(content("image/heic", width = 10, height = 10).metadata)
        assertEquals(AssetContent.AssetMetadata.Video(1920, 1080, 3_000), content("video/quicktime", 1920, 1080, 3_000).metadata)
        assertEquals(AssetContent.AssetMetadata.Audio(5_000, null), content("audio/x-m4a", durationMs = 5_000).metadata)
        assertNull(content("application/pdf").metadata)
    }

    @Test
    fun aLocalCopyLetsTheAppShowTheFileWithoutDownloading() = runTest {
        withDatabase { database ->
            val plain = byteArrayOf(1, 2, 3)
            val upload = UploadedFile(
                file = write("photo.jpg", plain),
                otrKey = AES256Key(byteArrayOf(1)),
                sha256 = SHA256Key(byteArrayOf(2)),
                assetId = UploadedAssetId("key", "wire.com"),
                encryptedSize = 32,
            )
            val assets = root / "storage" / "files"

            upload.keepLocalCopy(database, assets.toString())

            val asset = database.assetDAO.getAssetByKey("key").first()
            assertEquals((assets / "key.jpg").toString(), asset?.dataPath)
            assertEquals("wire.com", asset?.domain)
            assertContentEquals(plain, fileSystem.read(assets / "key.jpg") { readByteArray() })
            assertTrue(fileSystem.exists(root / "photo.jpg"), "The host keeps its file")
        }
    }

    @Test
    fun fileExtensionsFollowTheAppsRule() {
        assertEquals("jpg", "photo.jpg".fileExtension())
        assertEquals("gz", "archive.tar.gz".fileExtension())
        assertEquals("txt", ".notes.txt".fileExtension())
        assertNull(".profile".fileExtension())
        assertNull("README".fileExtension())
    }

    private fun write(name: String, bytes: ByteArray, mimeType: String = "image/jpeg"): ShareFile {
        fileSystem.createDirectories(root)
        val path = root / name
        fileSystem.write(path) { write(bytes) }
        return ShareFile(path.toString(), name, mimeType, bytes.size.toLong())
    }

    private fun uploads(api: AssetApi, auditLog: Boolean = false, key: AES256Key = generateRandomAES256Key()): ShareUploads {
        fileSystem.createDirectories(temporary)
        return ShareUploads(api, auditLog, temporary, fileSystem, newKey = { key })
    }

    private suspend fun TestScope.withDatabase(block: suspend (UserDatabaseBuilder) -> Unit) {
        val database = TestUserDatabase(QualifiedIDEntity(SELF.value, SELF.domain), StandardTestDispatcher(testScheduler))
        try {
            database.builder.userDAO.upsertUsers(listOf(self(team = null)))
            block(database.builder)
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    /** Records what each upload sends; reading the source fully, as the transport does. */
    private class RecordingAssetApi(private val failWith: KaliumException? = null) : AssetApi {
        val metadata = mutableListOf<AssetMetadataRequest>()
        val bodies = mutableListOf<ByteArray>()
        var attempts = 0

        override suspend fun uploadAsset(
            metadata: AssetMetadataRequest,
            encryptedDataSource: () -> Source,
            encryptedDataSize: Long
        ): NetworkResponse<AssetResponse> {
            attempts++
            failWith?.let { return NetworkResponse.Error(it) }
            val body = encryptedDataSource().buffer().use { it.readByteArray() }
            assertEquals(encryptedDataSize, body.size.toLong())
            this.metadata += metadata
            bodies += body
            return NetworkResponse.Success(AssetResponse("asset-${bodies.size}", "wire.com", null, "token-${bodies.size}"), emptyMap(), 201)
        }

        override suspend fun downloadAsset(assetId: String, assetDomain: String?, assetToken: String?, tempFileSink: Sink) =
            error("Not used")

        override suspend fun deleteAsset(assetId: String, assetDomain: String?, assetToken: String?) = error("Not used")
    }

    private companion object {
        val SELF = UserId("self", "wire.com")
        val CONVERSATION = ConversationId("conversation", "wire.com")

        fun self(team: String?) = UserEntity(
            id = QualifiedIDEntity(SELF.value, SELF.domain),
            name = "Self",
            handle = "self",
            email = null,
            phone = null,
            accentId = 1,
            team = team,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            expiresAt = null,
            defederated = false,
            supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = null,
        )
    }
}
