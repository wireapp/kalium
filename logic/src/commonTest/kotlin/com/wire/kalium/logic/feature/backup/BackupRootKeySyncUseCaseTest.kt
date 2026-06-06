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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.feature.message.TransactionalMessageSender
import dev.mokkery.mock
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.messaging.sending.BroadcastMessage
import com.wire.kalium.messaging.sending.BroadcastMessageTarget
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackupRootKeySyncUseCaseTest {

    @Test
    fun givenLocalKeyExists_whenSyncingBackupRootKey_thenNoRequestIsSent() = runTest {
        val arrangement = Arrangement()
        arrangement.repository.setBackupRootKey(KEY)
        val sync = arrangement.arrangeSync()

        val result = sync()

        assertEquals(SyncBackupRootKeyResult.LocalKeyExists, result)
        assertEquals(0, arrangement.messageSender.sentMessages.size)
    }

    @Test
    fun givenLocalKeyMissing_whenEnvelopeArrives_thenKeyIsStoredAndReturned() = runTest {
        val arrangement = Arrangement()
        arrangement.messageSender.onSend = { message, _ ->
            if (message.content is MessageContent.BackupRootKeySync.Request) {
                BackupRootKeySyncCoordinator.onEnvelope(REQUEST_ID, KEY)
            }
            Unit.right()
        }
        val sync = arrangement.arrangeSync(timeout = 1.seconds)

        val result = sync()

        assertIs<SyncBackupRootKeyResult.Found>(result)
        assertEquals(KEY.id, result.backupRootKey.id)
        assertEquals(KEY.id, arrangement.repository.getBackupRootKey()?.id)
        assertContentEquals(KEY.keyMaterial, arrangement.repository.getBackupRootKey()?.keyMaterial)
    }

    @Test
    fun givenLocalKeyMissing_whenNoClientResponds_thenUnavailableIsReturned() = runTest {
        val arrangement = Arrangement()
        val sync = arrangement.arrangeSync(timeout = 1.milliseconds)

        val result = sync()

        assertEquals(SyncBackupRootKeyResult.Unavailable, result)
        assertNull(arrangement.repository.getBackupRootKey())
    }

    @Test
    fun givenInvalidEnvelope_whenHandled_thenEnvelopeIsIgnored() = runTest {
        val arrangement = Arrangement()
        val handler = arrangement.arrangeHandler()

        handler.handle(
            mock<CryptoTransactionContext>(),
            signalingMessage(
                senderClientId = OTHER_CLIENT_ID,
                content = MessageContent.BackupRootKeySync.Envelope(
                    requestId = REQUEST_ID,
                    keyId = "",
                    keyMaterial = ByteArray(31),
                    createdAt = NOW,
                    createdByClientId = OTHER_CLIENT_ID,
                    version = 1,
                ),
            ),
            MessageContent.BackupRootKeySync.Envelope(
                requestId = REQUEST_ID,
                keyId = "",
                keyMaterial = ByteArray(31),
                createdAt = NOW,
                createdByClientId = OTHER_CLIENT_ID,
                version = 1,
            ),
        )

        assertNull(arrangement.repository.getBackupRootKey())
        assertEquals(0, arrangement.messageSender.sentMessages.size)
    }

    @Test
    fun givenNoSyncResponse_whenGettingOrCreatingKey_thenGeneratedKeyIsPushed() = runTest {
        val generatedKey = backupRootKey("generated")
        val arrangement = Arrangement()
        val getOrCreate = GetOrCreateSyncedBackupRootKeyUseCaseImpl(
            backupRootKeyRepository = arrangement.repository,
            syncBackupRootKey = object : SyncBackupRootKeyUseCase {
                override suspend fun invoke(): SyncBackupRootKeyResult = SyncBackupRootKeyResult.Unavailable
            },
            generateBackupRootKey = object : GenerateBackupRootKeyUseCase {
                override suspend fun invoke(): GenerateBackupRootKeyResult = GenerateBackupRootKeyResult.Success(generatedKey)
            },
            pushBackupRootKey = arrangement.pushUseCase,
        )

        val result = getOrCreate()

        assertIs<GetOrCreateSyncedBackupRootKeyResult.Success>(result)
        assertEquals(GetOrCreateSyncedBackupRootKeyResult.Source.GENERATED, result.source)
        assertEquals(generatedKey.id, arrangement.pushedKeys.single().id)
    }

    @Test
    fun givenForceGenerate_whenInvoked_thenGeneratedKeyIsPushed() = runTest {
        val generatedKey = backupRootKey("generated")
        val arrangement = Arrangement()
        val forceGenerate = GenerateAndForcePushBackupRootKeyUseCaseImpl(
            generateBackupRootKey = object : GenerateBackupRootKeyUseCase {
                override suspend fun invoke(): GenerateBackupRootKeyResult = GenerateBackupRootKeyResult.Success(generatedKey)
            },
            pushBackupRootKey = arrangement.pushUseCase,
        )

        val result = forceGenerate()

        assertIs<GenerateAndForcePushBackupRootKeyResult.Success>(result)
        assertEquals(generatedKey.id, result.backupRootKey.id)
        assertEquals(generatedKey.id, arrangement.pushedKeys.single().id)
    }

    @Test
    fun givenReceivedRequestAndLocalKeyExists_whenHandled_thenEnvelopeIsSentToRequester() = runTest {
        val arrangement = Arrangement()
        arrangement.repository.setBackupRootKey(KEY)
        val handler = arrangement.arrangeHandler()

        handler.handle(
            mock<CryptoTransactionContext>(),
            signalingMessage(OTHER_CLIENT_ID, MessageContent.BackupRootKeySync.Request(REQUEST_ID)),
            MessageContent.BackupRootKeySync.Request(REQUEST_ID),
        )

        val sent = arrangement.messageSender.sentMessages.single()
        val content = assertIs<MessageContent.BackupRootKeySync.Envelope>(sent.message.content)
        assertEquals(KEY.id, content.keyId)
        assertIs<MessageTarget.Client>(sent.target)
    }

    @Test
    fun givenReceivedRequestAndNoLocalKey_whenHandled_thenNothingIsSent() = runTest {
        val arrangement = Arrangement()
        val handler = arrangement.arrangeHandler()

        handler.handle(
            mock<CryptoTransactionContext>(),
            signalingMessage(OTHER_CLIENT_ID, MessageContent.BackupRootKeySync.Request(REQUEST_ID)),
            MessageContent.BackupRootKeySync.Request(REQUEST_ID),
        )

        assertEquals(0, arrangement.messageSender.sentMessages.size)
    }

    private class Arrangement {
        val repository = InMemoryBackupRootKeyRepository()
        val messageSender = RecordingMessageSender()
        val pushedKeys = mutableListOf<BackupRootKey>()
        val pushUseCase = object : PushBackupRootKeyUseCase {
            override suspend fun invoke(backupRootKey: BackupRootKey): PushBackupRootKeyResult {
                pushedKeys.add(backupRootKey)
                return PushBackupRootKeyResult.Success
            }
        }

        fun arrangeSync(timeout: kotlin.time.Duration = 1.seconds): SyncBackupRootKeyUseCase =
            SyncBackupRootKeyUseCaseImpl(
                selfUserId = SELF_USER_ID,
                currentClientIdProvider = CurrentClientIdProvider { CLIENT_ID.right() },
                backupRootKeyRepository = repository,
                selfConversationIdProvider = SelfConversationIdProvider { listOf(CONVERSATION_ID).right() },
                messageSender = messageSender,
                timeout = timeout,
                requestIdProvider = { REQUEST_ID },
            )

        fun arrangeHandler(): BackupRootKeyMessageHandler =
            BackupRootKeyMessageHandlerImpl(
                selfUserId = SELF_USER_ID,
                currentClientIdProvider = CurrentClientIdProvider { CLIENT_ID.right() },
                backupRootKeyRepository = repository,
                messageSender = messageSender,
            )
    }

    private class InMemoryBackupRootKeyRepository : BackupRootKeyRepository {
        private var backupRootKey: BackupRootKey? = null

        override fun getBackupRootKey(): BackupRootKey? = backupRootKey

        override fun setBackupRootKey(backupRootKey: BackupRootKey) {
            this.backupRootKey = backupRootKey
        }
    }

    private class RecordingMessageSender : MessageSender, TransactionalMessageSender {
        val sentMessages = mutableListOf<SentMessage>()
        var onSend: suspend (Message.Signaling, MessageTarget) -> Either<CoreFailure, Unit> = { _, _ -> Unit.right() }

        override suspend fun sendPendingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> =
            Unit.right()

        override suspend fun sendMessage(message: Message.Sendable, messageTarget: MessageTarget): Either<CoreFailure, Unit> {
            val signaling = message as Message.Signaling
            sentMessages.add(SentMessage(signaling, messageTarget))
            return onSend(signaling, messageTarget)
        }

        override suspend fun sendMessage(
            transactionContext: CryptoTransactionContext,
            message: Message.Sendable,
            messageTarget: MessageTarget,
        ): Either<CoreFailure, Unit> = sendMessage(message, messageTarget)

        override suspend fun broadcastMessage(
            message: BroadcastMessage,
            target: BroadcastMessageTarget,
        ): Either<CoreFailure, Unit> = Unit.right()
    }

    private data class SentMessage(
        val message: Message.Signaling,
        val target: MessageTarget,
    )

    companion object {
        private val SELF_USER_ID = QualifiedID("self", "example.com")
        private val CLIENT_ID = ClientId("client")
        private val OTHER_CLIENT_ID = ClientId("other-client")
        private val CONVERSATION_ID = QualifiedID("conversation", "example.com")
        private val NOW = Instant.parse("2026-01-01T00:00:00Z")
        private const val REQUEST_ID = "request-id"
        private val KEY = backupRootKey("synced-key")

        private fun backupRootKey(id: String): BackupRootKey =
            BackupRootKey(
                id = id,
                keyMaterial = ByteArray(32) { it.toByte() },
                createdAt = NOW,
                createdByClientId = OTHER_CLIENT_ID,
                version = 1,
            )

        private fun signalingMessage(
            senderClientId: ClientId,
            content: MessageContent.BackupRootKeySync,
        ): Message.Signaling =
            Message.Signaling(
                id = "message-id",
                content = content,
                conversationId = CONVERSATION_ID,
                date = NOW,
                senderUserId = SELF_USER_ID,
                senderClientId = senderClientId,
                status = Message.Status.Sent,
                isSelfMessage = true,
                expirationData = null,
            )
    }
}
