/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Sink
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssetPreviewRepositoryTest {
    @Test
    fun declaredOversizeIsRejectedBeforeCacheOrDownload() = runTest {
        val fixture = Fixture()

        fixture.repository.fetchPreviewAsset(fixture.preview.copy(sizeInBytes = MAX_SIZE + 1)).shouldFail()

        verifySuspend(VerifyMode.exactly(0)) {
            fixture.api.downloadAsset(any(), any(), any(), any())
            fixture.dao.getAssetByKey(any())
            fixture.dao.insertAsset(any())
        }
        fixture.assertNoDownloadFiles()
    }

    @Test
    fun lyingSenderIsStoppedWhileStreamingAndPartialDownloadIsRemoved() = runTest {
        val fixture = Fixture()
        var bytesWritten = 0L
        everySuspend { fixture.api.downloadAsset(any(), any(), any(), any()) } calls { invocation ->
            val sink = invocation.args[3] as Sink
            repeat(1024) {
                sink.write(Buffer().write(ByteArray(8192)), 8192)
                bytesWritten += 8192
            }
            NetworkResponse.Success(Unit, emptyMap(), 200)
        }

        fixture.repository.fetchPreviewAsset(fixture.preview.copy(sizeInBytes = 1)).shouldFail()

        assertTrue(bytesWritten in 1..MAX_ENCRYPTED_SIZE)
        assertTrue(bytesWritten < 1024 * 8192L)
        fixture.assertNothingPersisted()
    }

    @Test
    fun exactDecodedLimitSucceedsIncludingCbcIvAndFullPaddingBlock() = runTest {
        val fixture = Fixture()
        val preview = fixture.withEncryptedDownload(MAX_SIZE.toInt())

        fixture.repository.fetchPreviewAsset(preview).shouldSucceed {
            assertTrue(it.justDownloaded)
            assertEquals(MAX_SIZE, fixture.files.size(it.path))
        }

        verifySuspend(VerifyMode.exactly(1)) { fixture.dao.insertAsset(any()) }
        assertTrue(fixture.files.listDirectories(fixture.files.rootCachePath).isEmpty())
    }

    @Test
    fun concurrentCacheMissesReturnOneManagedFileAndRemoveUnusedCandidate() = runTest {
        val fixture = Fixture()
        val bothStarted = CompletableDeferred<Unit>()
        var downloads = 0
        val preview = fixture.withEncryptedDownload(1024) {
            if (++downloads == 2) bothStarted.complete(Unit)
            bothStarted.await()
        }
        val first = async { fixture.repository.fetchPreviewAsset(preview) }
        val second = async { fixture.repository.fetchPreviewAsset(preview) }
        var firstPath: okio.Path? = null
        first.await().shouldSucceed { firstPath = it.path }
        second.await().shouldSucceed { assertEquals(firstPath, it.path) }
        assertEquals(2, downloads)
        assertEquals(listOf(firstPath), fixture.files.listDirectories(firstPath!!.parent!!))
        assertTrue(fixture.files.listDirectories(fixture.files.rootCachePath).isEmpty())
    }

    @Test
    fun decodedSizeOneByteAboveLimitIsRejectedEvenWhenCiphertextFits() = runTest {
        val fixture = Fixture()
        // CBC encrypts 200000 and 200001 bytes to the same total size, so a second decoded limit is required.
        val preview = fixture.withEncryptedDownload(MAX_SIZE.toInt() + 1).copy(sizeInBytes = 1)

        fixture.repository.fetchPreviewAsset(preview).shouldFail()

        fixture.assertNothingPersisted()
    }

    @Test
    fun downloadFailureRemovesPartialFile() = runTest {
        val fixture = Fixture()
        everySuspend { fixture.api.downloadAsset(any(), any(), any(), any()) } calls { invocation ->
            (invocation.args[3] as Sink).write(Buffer().writeUtf8("partial"), 7)
            NetworkResponse.Error(KaliumException.GenericError(IllegalStateException("download failed")))
        }

        fixture.repository.fetchPreviewAsset(fixture.preview).shouldFail()

        fixture.assertNothingPersisted()
    }

    @Test
    fun cancellationPropagatesAndRemovesPartialDownload() = runTest {
        val fixture = Fixture()
        everySuspend { fixture.api.downloadAsset(any(), any(), any(), any()) } calls { invocation ->
            (invocation.args[3] as Sink).write(Buffer().writeUtf8("partial"), 7)
            throw CancellationException("cancelled download")
        }

        assertFailsWith<CancellationException> { fixture.repository.fetchPreviewAsset(fixture.preview) }

        fixture.assertNothingPersisted()
    }

    @Test
    fun cancellationAfterDecodeRemovesDecodedFileWithoutAcceptingCacheRecord() = runTest {
        val fixture = Fixture()
        val preview = fixture.withEncryptedDownload(1024)
        everySuspend { fixture.dao.insertAsset(any()) } calls {
            throw CancellationException("cancelled before cache persistence")
        }

        assertFailsWith<CancellationException> { fixture.repository.fetchPreviewAsset(preview) }

        fixture.assertNoDownloadFiles()
    }

    @Test
    fun cancellationAfterDatabaseCommitPreservesReferencedFile() = runTest {
        val fixture = Fixture()
        val preview = fixture.withEncryptedDownload(1024)
        everySuspend { fixture.dao.insertAsset(any()) } calls { invocation ->
            fixture.stored = invocation.args[0] as AssetEntity
            currentCoroutineContext().cancel()
            currentCoroutineContext().ensureActive()
        }

        val download = async { fixture.repository.fetchPreviewAsset(preview) }
        assertFailsWith<CancellationException> { download.await() }

        val committedPath = requireNotNull(fixture.stored).dataPath.toPath()
        assertEquals(1024L, fixture.files.size(committedPath))
        assertEquals(listOf(committedPath), fixture.files.listDirectories(committedPath.parent!!))
        assertTrue(fixture.files.listDirectories(fixture.files.rootCachePath).isEmpty())
    }

    @Test
    fun wrongHashRemovesDownloadedAndDecodedFiles() = runTest {
        val fixture = Fixture()
        val preview = fixture.withEncryptedDownload(1024)
            .copy(remoteData = fixture.preview.remoteData.copy(sha256 = ByteArray(32) { 9 }))

        fixture.repository.fetchPreviewAsset(preview).shouldFail()

        fixture.assertNothingPersisted()
    }

    @Test
    fun oversizedCachedFileIsRefusedWithoutRemovingSharedAsset() = runTest {
        val fixture = Fixture()
        val path = fixture.files.providePersistentAssetPath("shared-asset.jpg")
        fixture.files.sink(path).buffer().use { it.write(ByteArray(MAX_SIZE.toInt() + 1)) }
        // The cache record can lie too: trust the on-disk size, while preserving other attachment references.
        everySuspend { fixture.dao.getAssetByKey(fixture.preview.remoteData.assetId) } returns flowOf(
            AssetEntity(fixture.preview.remoteData.assetId, "domain", path.toString(), 1, null, 1)
        )

        fixture.repository.fetchPreviewAsset(fixture.preview).shouldFail()

        assertEquals(MAX_SIZE + 1, fixture.files.size(path))
        verifySuspend(VerifyMode.exactly(0)) {
            fixture.api.downloadAsset(any(), any(), any(), any())
            fixture.dao.deleteAsset(any())
            fixture.dao.insertAsset(any())
        }
    }

    @Test
    fun cachedFileAtLimitIsAcceptedWithoutDownload() = runTest {
        val fixture = Fixture()
        val path = fixture.files.providePersistentAssetPath("cached-preview.jpg")
        fixture.files.sink(path).buffer().use { it.write(ByteArray(MAX_SIZE.toInt())) }
        everySuspend { fixture.dao.getAssetByKey(fixture.preview.remoteData.assetId) } returns flowOf(
            AssetEntity(fixture.preview.remoteData.assetId, "domain", path.toString(), MAX_SIZE, null, 1)
        )

        fixture.repository.fetchPreviewAsset(fixture.preview).shouldSucceed {
            assertEquals(path, it.path)
            assertEquals(false, it.justDownloaded)
        }

        verifySuspend(VerifyMode.exactly(0)) { fixture.api.downloadAsset(any(), any(), any(), any()) }
    }

    private class Fixture {
        val files = FakeKaliumFileSystem()
        val api = mock<AssetApi>()
        val dao = mock<AssetDAO>()
        private val key = generateRandomAES256Key()
        val preview = AssetContent.Preview(
            "image/jpeg", MAX_SIZE, 512, 288,
            AssetContent.RemoteData(key.data, ByteArray(32) { 1 }, "preview-id", null, "domain", MessageEncryptionAlgorithm.AES_CBC),
        )
        val repository: AssetRepository = AssetDataSource(
            api, dao, UserId("self", "domain"), AssetMapperImpl(), lazy { mock<AssetAuditFeatureHandler>() }, files
        )

        var stored: AssetEntity? = null

        init {
            every { dao.getAssetByKey(any()) } calls { flowOf(stored) }
            everySuspend { dao.isFileReferenced(any()) } calls {
                currentCoroutineContext().ensureActive()
                stored?.dataPath == it.args[0]
            }
            everySuspend { dao.insertAsset(any()) } calls { invocation ->
                if (stored == null) stored = invocation.args[0] as AssetEntity
                Unit
            }
        }

        fun withEncryptedDownload(decodedSize: Int, beforeDownload: suspend () -> Unit = {}): AssetContent.Preview {
            val encrypted = Buffer()
            encryptFileWithAES256(Buffer().write(ByteArray(decodedSize) { 3 }), key, encrypted)
            val bytes = encrypted.readByteArray()
            if (decodedSize >= MAX_SIZE) assertEquals(MAX_ENCRYPTED_SIZE, bytes.size.toLong())
            val hash = SHA256Key(calcFileSHA256(Buffer().write(bytes))!!)
            everySuspend { api.downloadAsset(any(), any(), any(), any()) } calls { invocation ->
                beforeDownload()
                val sink = invocation.args[3] as Sink
                val buffer = Buffer().write(bytes)
                while (buffer.size > 0) sink.write(buffer, minOf(8192, buffer.size))
                NetworkResponse.Success(Unit, emptyMap(), 200)
            }
            return preview.copy(sizeInBytes = decodedSize.toLong(), remoteData = preview.remoteData.copy(sha256 = hash.data))
        }

        suspend fun assertNoDownloadFiles() {
            assertTrue(files.listDirectories(files.rootCachePath).isEmpty())
            assertTrue(files.listDirectories(files.providePersistentAssetPath("preview.jpg").parent!!).isEmpty())
        }

        suspend fun assertNothingPersisted() {
            assertNoDownloadFiles()
            verifySuspend(VerifyMode.exactly(0)) { dao.insertAsset(any()) }
        }
    }

    private companion object {
        const val MAX_SIZE = 200_000L
        const val MAX_ENCRYPTED_SIZE = 200_032L
    }
}
