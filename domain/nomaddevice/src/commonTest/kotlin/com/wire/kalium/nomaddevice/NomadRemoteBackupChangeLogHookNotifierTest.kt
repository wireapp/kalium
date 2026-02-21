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

package com.wire.kalium.nomaddevice

import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.ChangeLogEntry
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NomadRemoteBackupChangeLogHookNotifierTest {

    @Test
    fun givenTextAssetLocation_whenCallbackInvoked_thenMessageUpsertsAreLogged() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val callback = createCallback(coroutineScope = this, daoProvider = { dao })

        callback(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)
        callback(persistedMessage(content = MessageContent.Asset(assetContent("asset-1"))), SELF_USER_ID)
        callback(persistedMessage(content = MessageContent.Location(1.0f, 2.0f)), SELF_USER_ID)
        runCurrent()

        assertEquals(3, dao.messageUpsertCalls.size)
        assertEquals(EVENT_TIMESTAMP_MS, dao.messageUpsertCalls[0].timestampMs)
        assertEquals(MESSAGE_TIMESTAMP_MS, dao.messageUpsertCalls[0].messageTimestampMs)
    }

    @Test
    fun givenMultipartWithSupportedPart_whenCallbackInvoked_thenMessageUpsertIsLogged() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val callback = createCallback(coroutineScope = this, daoProvider = { dao })

        callback(
            persistedMessage(
                content = MessageContent.Multipart(
                    value = null,
                    attachments = listOf(unsupportedAttachment(), assetContent("asset-multipart"))
                )
            ),
            SELF_USER_ID
        )
        runCurrent()

        assertEquals(1, dao.messageUpsertCalls.size)
    }

    @Test
    fun givenMultipartWithOnlyUnsupportedParts_whenCallbackInvoked_thenEntryIsSkipped() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val callback = createCallback(coroutineScope = this, daoProvider = { dao })

        callback(
            persistedMessage(
                content = MessageContent.Multipart(
                    value = null,
                    attachments = listOf(unsupportedAttachment())
                )
            ),
            SELF_USER_ID
        )
        runCurrent()

        assertTrue(dao.messageUpsertCalls.isEmpty())
    }

    @Test
    fun givenUnsupportedMessageContent_whenCallbackInvoked_thenEntryIsSkipped() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val callback = createCallback(coroutineScope = this, daoProvider = { dao })

        callback(
            persistedMessage(content = MessageContent.Reaction(messageId = MESSAGE_ID, emojiSet = setOf(":+1:"))),
            SELF_USER_ID
        )
        runCurrent()

        assertTrue(dao.messageUpsertCalls.isEmpty())
    }

    @Test
    fun givenMissingStorage_whenCallbackInvoked_thenItWarnsAndSkips() = runTest {
        val warnings = mutableListOf<String>()
        val callback = createCallback(
            coroutineScope = this,
            daoProvider = { null },
            warnLogger = { warnings.add(it) }
        )

        callback(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)
        runCurrent()

        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("missing user storage", ignoreCase = true))
    }

    @Test
    fun givenDaoFailure_whenCallbackInvoked_thenFailureIsCaughtAndLogged() = runTest {
        val errors = mutableListOf<String>()
        val callback = createCallback(
            coroutineScope = this,
            daoProvider = { RecordingRemoteBackupChangeLogDAO(throwOnMessageUpsert = IllegalStateException("boom")) },
            errorLogger = { message, _ -> errors.add(message) }
        )

        callback(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)
        runCurrent()

        assertEquals(1, errors.size)
        assertTrue(errors.first().contains("Failed to write MESSAGE_UPSERT changelog"))
    }

    @Test
    fun givenDedicatedNotifier_whenInvoked_thenItDelegatesToCallback() {
        var invocationCount = 0
        val notifier = NomadRemoteBackupChangeLogHookNotifier { _, _ -> invocationCount++ }

        notifier.onMessagePersisted(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)

        assertEquals(1, invocationCount)
    }

    @Test
    fun givenCallbackPath_whenInvoked_thenItNeverThrowsSynchronously() = runTest {
        val callback = createCallback(
            coroutineScope = this,
            daoProvider = { RecordingRemoteBackupChangeLogDAO(throwOnMessageUpsert = IllegalStateException("boom")) }
        )

        val result = runCatching {
            callback(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)
        }

        assertTrue(result.isSuccess)
    }

    private fun createCallback(
        coroutineScope: CoroutineScope,
        daoProvider: (UserId) -> RemoteBackupChangeLogDAO?,
        warnLogger: (String) -> Unit = {},
        errorLogger: (String, Throwable) -> Unit = { _, _ -> }
    ): (PersistedMessageData, UserId) -> Unit =
        createNomadRemoteBackupChangeLogCallbackInternal(
            remoteBackupChangeLogDAOProvider = daoProvider,
            coroutineScope = coroutineScope,
            eventTimestampMsProvider = { EVENT_TIMESTAMP_MS },
            warnLogger = warnLogger,
            errorLogger = errorLogger
        )

    private fun persistedMessage(content: MessageContent): PersistedMessageData =
        PersistedMessageData(
            conversationId = CONVERSATION_ID_MODEL,
            messageId = MESSAGE_ID,
            content = content,
            date = Instant.fromEpochMilliseconds(MESSAGE_TIMESTAMP_MS),
            expireAfter = null
        )

    private fun assetContent(assetId: String): AssetContent =
        AssetContent(
            sizeInBytes = 42L,
            mimeType = "image/png",
            remoteData = AssetContent.RemoteData(
                otrKey = byteArrayOf(1),
                sha256 = byteArrayOf(2),
                assetId = assetId,
                assetToken = null,
                assetDomain = null,
                encryptionAlgorithm = null
            )
        )

    private fun unsupportedAttachment(): CellAssetContent =
        CellAssetContent(
            id = "cell-attachment-id",
            versionId = "v1",
            mimeType = "application/octet-stream",
            assetPath = null,
            assetSize = null,
            metadata = null,
            transferStatus = AssetTransferStatus.NOT_DOWNLOADED
        )

    private class RecordingRemoteBackupChangeLogDAO(
        private val throwOnMessageUpsert: Throwable? = null
    ) : RemoteBackupChangeLogDAO {

        data class MessageUpsertCall(
            val conversationId: QualifiedIDEntity,
            val messageId: String,
            val timestampMs: Long,
            val messageTimestampMs: Long
        )

        val messageUpsertCalls = mutableListOf<MessageUpsertCall>()

        override suspend fun logMessageUpsert(
            conversationId: QualifiedIDEntity,
            messageId: String,
            timestampMs: Long,
            messageTimestampMs: Long
        ) {
            throwOnMessageUpsert?.let { throw it }
            messageUpsertCalls.add(
                MessageUpsertCall(
                    conversationId = conversationId,
                    messageId = messageId,
                    timestampMs = timestampMs,
                    messageTimestampMs = messageTimestampMs
                )
            )
        }

        override suspend fun logMessageDelete(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) =
            error("Unexpected invocation")

        override suspend fun logReactionsSync(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) =
            error("Unexpected invocation")

        override suspend fun logReadReceiptsSync(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) =
            error("Unexpected invocation")

        override suspend fun logConversationDelete(conversationId: QualifiedIDEntity, timestampMs: Long) =
            error("Unexpected invocation")

        override suspend fun logConversationClear(conversationId: QualifiedIDEntity, timestampMs: Long) =
            error("Unexpected invocation")

        override suspend fun getPendingChanges(): List<ChangeLogEntry> = emptyList()
    }

    private companion object {
        const val EVENT_TIMESTAMP_MS = 1234L
        const val MESSAGE_TIMESTAMP_MS = 9876L
        const val MESSAGE_ID = "message-id"
        val CONVERSATION_ID_MODEL = QualifiedID("conversation-id", "wire.test")
        val SELF_USER_ID = QualifiedID("self-user-id", "wire.test")
    }
}
