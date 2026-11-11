/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.pendingConversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewImageLimits
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ReceivedLinkPreviewImagesTest {
    @Test
    fun lyingSenderIsStoppedDuringStreamingAndPartialFilesAreRemoved() = runTest {
        fixture {
            var written = 0L
            api.download = { sink ->
                repeat(1024) {
                    sink.write(Buffer().write(ByteArray(8192)), 8192)
                    written += 8192
                }
            }

            resolve(image)

            assertTrue(written in 1..LinkPreviewImageLimits.MAX_ENCRYPTED_SIZE_IN_BYTES)
            assertNothingPersisted()
        }
    }

    @Test
    fun decodedLimitIsCheckedSeparatelyFromEncryptedLimit() = runTest {
        for (size in listOf(MAX_SIZE, MAX_SIZE + 1)) fixture {
            val downloaded = withEncryptedDownload(size)

            resolve(downloaded)

            if (size == MAX_SIZE) {
                val asset = assertNotNull(db.assetDAO.getAssetByKey("image").first())
                assertEquals(size.toLong(), files.metadata(asset.dataPath.toPath()).size)
                assertEquals(asset.dataPath, localPaths().single())
                assertEquals(listOf(asset.dataPath.toPath()), files.list(directory))
            } else assertNothingPersisted()
        }
    }

    @Test
    fun cachedPreviewUsesActualFileSizeAndPreservesSharedFiles() = runTest {
        for (size in listOf(0, MAX_SIZE, MAX_SIZE + 1)) fixture {
            val path = directory.resolve("cached")
            files.write(path) { write(ByteArray(size)) }
            db.assetDAO.insertAsset(AssetEntity("image", "wire.com", path.toString(), 1, null, 1))

            resolve(image)

            assertEquals(if (size == MAX_SIZE) path.toString() else null, localPaths().single())
            assertEquals(0, api.downloads)
            assertEquals(size.toLong(), files.metadata(path).size)
        }
    }

    @Test
    fun excessivePreviewsDownloadOnlyTheFirstImage() = runTest {
        fixture {
            resolve(withEncryptedDownload(1024), count = 50)

            assertEquals(LinkPreviewImageLimits.MAX_PREVIEWS_PER_MESSAGE, api.downloads)
            assertEquals(LinkPreviewImageLimits.MAX_PREVIEWS_PER_MESSAGE, localPaths().count { it != null })
        }
    }

    @Test
    fun cancellationPropagatesAndRemovesPartialDownloads() = runTest {
        fixture {
            api.download = { sink ->
                sink.write(Buffer().writeUtf8("partial"), 7)
                throw CancellationException("cancelled preview")
            }

            assertFailsWith<CancellationException> { resolve(image) }

            assertNothingPersisted()
        }
    }

    private suspend fun TestScope.fixture(block: suspend Fixture.() -> Unit) {
        withTestUserDatabase(self) { database ->
            val fixture = Fixture(database.builder)
            try {
                fixture.db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self))
                fixture.db.conversationDAO.insertConversation(
                    ConnectionEntity(
                        conversation.value, self.value, time, conversation, self,
                        ConnectionEntity.State.ACCEPTED, self.value,
                    ).pendingConversation()
                )
                fixture.block()
            } finally {
                fixture.files.deleteRecursively(fixture.root)
            }
        }
    }

    private class Fixture(val db: UserDatabaseBuilder) {
        val files = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("received-previews-${Uuid.random()}")
        val directory = root.resolve("nse-previews")
        val api = PreviewAssetApi()
        val images = ReceivedLinkPreviewImages(true, api, root.toString())
        val image = LinkPreviewAsset(
            mimeType = "image/png", assetDataPath = null, assetDataSize = 1, assetHeight = 1, assetWidth = 1,
            assetKey = "image", assetDomain = "wire.com", otrKey = ByteArray(32) { 1 }, sha256Key = ByteArray(32) { 2 },
        )

        init {
            files.createDirectories(directory)
        }

        suspend fun resolve(image: LinkPreviewAsset, count: Int = 1) {
            val previews = List(count) { MessageLinkPreview("https://wire.com", it, image = image) }
            db.messageDAO.insertOrIgnoreMessage(
                MessageEntity.Regular(
                    id = "message", content = MessageEntityContent.Text("text", linkPreview = previews.map {
                        MessageEntity.LinkPreview(it.url, it.urlOffset, it.url, "title", "summary")
                    }), conversationId = conversation, date = time, senderUserId = self, senderClientId = "client",
                    status = MessageEntity.Status.SENT, editStatus = MessageEntity.EditStatus.NotEdited,
                    visibility = MessageEntity.Visibility.VISIBLE, senderName = null, expectsReadConfirmation = false,
                    readCount = 0, expireAfterMs = null, selfDeletionEndDate = null, sender = null,
                )
            )
            val message = Message.Regular(
                "message", MessageContent.Text("text", linkPreviews = previews), ConversationId(conversation.value, conversation.domain),
                time, UserId(self.value, self.domain), Message.Status.Sent, isSelfMessage = false,
                senderClientId = ClientId("client"), editStatus = Message.EditStatus.NotEdited, linkPreviews = previews,
            )
            db.writeMessageEffects { images.resolve(this, message) }
        }

        fun withEncryptedDownload(size: Int): LinkPreviewAsset {
            val encrypted = Buffer()
            encryptFileWithAES256(Buffer().write(ByteArray(size) { 3 }), AES256Key(image.otrKey), encrypted)
            val bytes = encrypted.readByteArray()
            assertTrue(bytes.size <= LinkPreviewImageLimits.MAX_ENCRYPTED_SIZE_IN_BYTES)
            api.download = { sink ->
                val buffer = Buffer().write(bytes)
                while (buffer.size > 0) sink.write(buffer, minOf(8192, buffer.size))
            }
            return image.copy(sha256Key = checkNotNull(calcFileSHA256(Buffer().write(bytes))))
        }

        suspend fun localPaths(): List<String?> =
            (db.messageDAO.getMessageById("message", conversation)?.content as? MessageEntityContent.Text)
                ?.linkPreview.orEmpty().map { it.imageLocalPath?.takeIf(String::isNotBlank) }

        suspend fun assertNothingPersisted() {
            assertNull(db.assetDAO.getAssetByKey("image").first())
            assertTrue(localPaths().all { it == null })
            assertTrue(files.list(directory).isEmpty())
        }
    }

    private class PreviewAssetApi : AssetApi {
        var downloads = 0
        var download: suspend (Sink) -> Unit = { error("Unexpected download") }

        override suspend fun downloadAsset(
            assetId: String, assetDomain: String?, assetToken: String?, tempFileSink: Sink
        ): NetworkResponse<Unit> {
            downloads++
            download(tempFileSink)
            return NetworkResponse.Success(Unit, emptyMap(), 200)
        }

        override suspend fun uploadAsset(
            metadata: AssetMetadataRequest, encryptedDataSource: () -> Source, encryptedDataSize: Long
        ): NetworkResponse<AssetResponse> = error("Unexpected upload")

        override suspend fun deleteAsset(assetId: String, assetDomain: String?, assetToken: String?): NetworkResponse<Unit> =
            error("Unexpected delete")
    }

    private companion object {
        val MAX_SIZE = LinkPreviewImageLimits.MAX_SIZE_IN_BYTES.toInt()
        val self = QualifiedIDEntity("preview-self", "wire.com")
        val conversation = QualifiedIDEntity("preview-conversation", "wire.com")
        val time = Instant.fromEpochMilliseconds(0)
    }
}
