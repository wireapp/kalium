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
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.ChangeLogEntry
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NomadRemoteBackupChangeLogHookNotifierTest {

    // region MESSAGE_UPSERT tests

    @Test
    fun givenTextAssetLocation_whenCallbackInvoked_thenMessageUpsertsAreLogged() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onMessagePersisted(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)
        notifier.onMessagePersisted(persistedMessage(content = MessageContent.Asset(assetContent("asset-1"))), SELF_USER_ID)
        notifier.onMessagePersisted(persistedMessage(content = MessageContent.Location(1.0f, 2.0f)), SELF_USER_ID)

        assertEquals(3, dao.messageUpsertCalls.size)
        assertEquals(EVENT_TIMESTAMP_MS, dao.messageUpsertCalls[0].timestampMs)
        assertEquals(MESSAGE_TIMESTAMP_MS, dao.messageUpsertCalls[0].messageTimestampMs)
    }

    @Test
    fun givenMultipartWithSupportedPart_whenCallbackInvoked_thenMessageUpsertIsLogged() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onMessagePersisted(
            persistedMessage(
                content = MessageContent.Multipart(
                    value = null,
                    attachments = listOf(unsupportedAttachment(), assetContent("asset-multipart"))
                )
            ),
            SELF_USER_ID
        )

        assertEquals(1, dao.messageUpsertCalls.size)
    }

    @Test
    fun givenMultipartWithTextPartOnly_whenCallbackInvoked_thenMessageUpsertIsLogged() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onMessagePersisted(
            persistedMessage(
                content = MessageContent.Multipart(
                    value = "multipart-text-only",
                    attachments = emptyList()
                )
            ),
            SELF_USER_ID
        )

        assertEquals(1, dao.messageUpsertCalls.size)
    }

    @Test
    fun givenMultipartWithOnlyUnsupportedParts_whenCallbackInvoked_thenEntryIsSkipped() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onMessagePersisted(
            persistedMessage(
                content = MessageContent.Multipart(
                    value = null,
                    attachments = listOf(unsupportedAttachment())
                )
            ),
            SELF_USER_ID
        )

        assertTrue(dao.messageUpsertCalls.isEmpty())
    }

    @Test
    fun givenUnsupportedMessageContent_whenCallbackInvoked_thenEntryIsSkipped() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onMessagePersisted(
            persistedMessage(content = MessageContent.Reaction(messageId = MESSAGE_ID, emojiSet = setOf(":+1:"))),
            SELF_USER_ID
        )

        assertTrue(dao.messageUpsertCalls.isEmpty())
    }

    @Test
    fun givenDedicatedNotifier_whenInvoked_thenItDelegatesToRepository() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onMessagePersisted(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)

        assertEquals(1, dao.messageUpsertCalls.size)
    }

    @Test
    fun givenCallbackPath_whenInvoked_thenItNeverThrowsSynchronously() = runTest {
        val notifier = createNotifier(
            daoProvider = { RecordingRemoteBackupChangeLogDAO(throwOnMessageUpsert = IllegalStateException("boom")) }
        )

        var didThrow = false
        try {
            notifier.onMessagePersisted(persistedMessage(content = MessageContent.Text("hello")), SELF_USER_ID)
        } catch (throwable: Throwable) {
            didThrow = true
        }

        assertTrue(!didThrow)
    }

    // endregion

    // region MESSAGE_DELETE tests

    @Test
    fun givenMessageDeleteEvent_whenNotifierInvoked_thenLogMessageDeleteIsCalled() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onMessageDeleted(
            MessageDeleteEventData(CONVERSATION_ID_MODEL, MESSAGE_ID),
            SELF_USER_ID
        )

        assertEquals(1, dao.messageDeleteCalls.size)
        assertEquals(CONVERSATION_ID_MODEL.toQualifiedIDEntity(), dao.messageDeleteCalls[0].conversationId)
        assertEquals(MESSAGE_ID, dao.messageDeleteCalls[0].messageId)
        assertEquals(EVENT_TIMESTAMP_MS, dao.messageDeleteCalls[0].timestampMs)
    }

    // endregion

    // region REACTIONS_SYNC tests

    @Test
    fun givenReactionEvent_whenNotifierInvoked_thenLogReactionsSyncIsCalled() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onReactionPersisted(
            ReactionEventData(CONVERSATION_ID_MODEL, MESSAGE_ID, Instant.fromEpochMilliseconds(MESSAGE_TIMESTAMP_MS)),
            SELF_USER_ID
        )

        assertEquals(1, dao.reactionsSyncCalls.size)
        assertEquals(CONVERSATION_ID_MODEL.toQualifiedIDEntity(), dao.reactionsSyncCalls[0].conversationId)
        assertEquals(MESSAGE_ID, dao.reactionsSyncCalls[0].messageId)
        assertEquals(EVENT_TIMESTAMP_MS, dao.reactionsSyncCalls[0].timestampMs)
    }

    // endregion

    // region READ_RECEIPTS_SYNC tests

    @Test
    fun givenReadReceiptEvent_whenNotifierInvoked_thenLogReadReceiptsSyncIsCalledForEachMessageId() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })
        val messageIds = listOf("msg-1", "msg-2", "msg-3")

        notifier.onReadReceiptPersisted(
            ReadReceiptEventData(CONVERSATION_ID_MODEL, messageIds, Instant.fromEpochMilliseconds(MESSAGE_TIMESTAMP_MS)),
            SELF_USER_ID
        )

        assertEquals(3, dao.readReceiptsSyncCalls.size)
        assertEquals("msg-1", dao.readReceiptsSyncCalls[0].messageId)
        assertEquals("msg-2", dao.readReceiptsSyncCalls[1].messageId)
        assertEquals("msg-3", dao.readReceiptsSyncCalls[2].messageId)
    }

    // endregion

    // region CONVERSATION_DELETE tests

    @Test
    fun givenConversationDeleteEvent_whenNotifierInvoked_thenLogConversationDeleteIsCalled() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onConversationDeleted(
            ConversationDeleteEventData(CONVERSATION_ID_MODEL),
            SELF_USER_ID
        )

        assertEquals(1, dao.conversationDeleteCalls.size)
        assertEquals(CONVERSATION_ID_MODEL.toQualifiedIDEntity(), dao.conversationDeleteCalls[0].conversationId)
        assertEquals(EVENT_TIMESTAMP_MS, dao.conversationDeleteCalls[0].timestampMs)
    }

    // endregion

    // region CONVERSATION_CLEAR tests

    @Test
    fun givenConversationClearEvent_whenNotifierInvoked_thenLogConversationClearIsCalled() = runTest {
        val dao = RecordingRemoteBackupChangeLogDAO()
        val notifier = createNotifier(daoProvider = { dao })

        notifier.onConversationCleared(
            ConversationClearEventData(CONVERSATION_ID_MODEL),
            SELF_USER_ID
        )

        assertEquals(1, dao.conversationClearCalls.size)
        assertEquals(CONVERSATION_ID_MODEL.toQualifiedIDEntity(), dao.conversationClearCalls[0].conversationId)
        assertEquals(EVENT_TIMESTAMP_MS, dao.conversationClearCalls[0].timestampMs)
    }

    // endregion

    private fun createNotifier(
        daoProvider: (UserId) -> RemoteBackupChangeLogDAO?,
    ): PersistenceEventHookNotifier =
        createNomadRemoteBackupChangeLogHookNotifierInternal(
            remoteBackupChangeLogDAOProvider = daoProvider,
            eventTimestampMsProvider = { EVENT_TIMESTAMP_MS },
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

    private fun QualifiedID.toQualifiedIDEntity() = QualifiedIDEntity(value, domain)

    private class RecordingRemoteBackupChangeLogDAO(
        private val throwOnMessageUpsert: Throwable? = null,
        private val throwOnMessageDelete: Throwable? = null,
        private val throwOnReactionsSync: Throwable? = null,
        private val throwOnReadReceiptsSync: Throwable? = null,
        private val throwOnConversationDelete: Throwable? = null,
        private val throwOnConversationClear: Throwable? = null,
    ) : RemoteBackupChangeLogDAO {

        data class MessageUpsertCall(
            val conversationId: QualifiedIDEntity,
            val messageId: String,
            val timestampMs: Long,
            val messageTimestampMs: Long,
        )

        data class MessageDeleteCall(
            val conversationId: QualifiedIDEntity,
            val messageId: String,
            val timestampMs: Long,
        )

        data class ReactionsSyncCall(
            val conversationId: QualifiedIDEntity,
            val messageId: String,
            val timestampMs: Long,
        )

        data class ReadReceiptsSyncCall(
            val conversationId: QualifiedIDEntity,
            val messageId: String,
            val timestampMs: Long,
        )

        data class ConversationDeleteCall(
            val conversationId: QualifiedIDEntity,
            val timestampMs: Long,
        )

        data class ConversationClearCall(
            val conversationId: QualifiedIDEntity,
            val timestampMs: Long,
        )

        val messageUpsertCalls = mutableListOf<MessageUpsertCall>()
        val messageDeleteCalls = mutableListOf<MessageDeleteCall>()
        val reactionsSyncCalls = mutableListOf<ReactionsSyncCall>()
        val readReceiptsSyncCalls = mutableListOf<ReadReceiptsSyncCall>()
        val conversationDeleteCalls = mutableListOf<ConversationDeleteCall>()
        val conversationClearCalls = mutableListOf<ConversationClearCall>()

        override suspend fun logMessageUpsert(
            conversationId: QualifiedIDEntity,
            messageId: String,
            timestampMs: Long,
            messageTimestampMs: Long
        ) {
            throwOnMessageUpsert?.let { throw it }
            messageUpsertCalls.add(MessageUpsertCall(conversationId, messageId, timestampMs, messageTimestampMs))
        }

        override suspend fun logMessageDelete(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) {
            throwOnMessageDelete?.let { throw it }
            messageDeleteCalls.add(MessageDeleteCall(conversationId, messageId, timestampMs))
        }

        override suspend fun logReactionsSync(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) {
            throwOnReactionsSync?.let { throw it }
            reactionsSyncCalls.add(ReactionsSyncCall(conversationId, messageId, timestampMs))
        }

        override suspend fun logReadReceiptsSync(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) {
            throwOnReadReceiptsSync?.let { throw it }
            readReceiptsSyncCalls.add(ReadReceiptsSyncCall(conversationId, messageId, timestampMs))
        }

        override suspend fun logConversationDelete(conversationId: QualifiedIDEntity, timestampMs: Long) {
            throwOnConversationDelete?.let { throw it }
            conversationDeleteCalls.add(ConversationDeleteCall(conversationId, timestampMs))
        }

        override suspend fun logConversationClear(conversationId: QualifiedIDEntity, timestampMs: Long) {
            throwOnConversationClear?.let { throw it }
            conversationClearCalls.add(ConversationClearCall(conversationId, timestampMs))
        }

        override suspend fun getPendingChanges(): List<ChangeLogEntry> = emptyList()

        override suspend fun getLastPendingChangesBatch(limit: Long): com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch =
            com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch(emptyList(), emptyList())

        override fun observeLastPendingChangesBatch(limit: Long): Flow<com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch> =
            flowOf(com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch(emptyList(), emptyList()))

        override suspend fun deleteChanges(changes: List<ChangeLogEntry>) {
            // Not needed in these tests.
        }
    }

    private companion object {
        const val EVENT_TIMESTAMP_MS = 1234L
        const val MESSAGE_TIMESTAMP_MS = 9876L
        const val MESSAGE_ID = "message-id"
        val CONVERSATION_ID_MODEL = QualifiedID("conversation-id", "wire.test")
        val SELF_USER_ID = QualifiedID("self-user-id", "wire.test")
    }
}
