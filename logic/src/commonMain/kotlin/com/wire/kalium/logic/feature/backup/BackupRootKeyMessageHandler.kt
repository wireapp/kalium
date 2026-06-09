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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

internal interface BackupRootKeyMessageHandler {
    suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        content: MessageContent.BackupRootKeySync,
    )

    suspend fun flushPendingMessages()
}

internal class BackupRootKeyMessageHandlerImpl(
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val backupRootKeyRepository: BackupRootKeyRepository,
    private val messageSender: MessageSender,
    private val coordinator: BackupRootKeySyncCoordinator = BackupRootKeySyncCoordinator,
    private val pendingRequestStore: BackupRootKeyPendingRequestStore,
) : BackupRootKeyMessageHandler {

    private val pendingMessagesMutex = Mutex()
    private val pendingMessages = mutableListOf<PendingMessage>()

    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        content: MessageContent.BackupRootKeySync,
    ) {
        val currentClientId = when (val result = currentClientIdProvider()) {
            is Either.Left -> return
            is Either.Right -> result.value
        }
        if (message.senderClientId == currentClientId) return

        when (content) {
            is MessageContent.BackupRootKeySync.Request -> handleRequest(message, content)
            is MessageContent.BackupRootKeySync.Envelope -> handleEnvelope(message, content, currentClientId)
            is MessageContent.BackupRootKeySync.Ack -> Unit
        }
    }

    private suspend fun handleRequest(
        message: Message.Signaling,
        content: MessageContent.BackupRootKeySync.Request,
    ) {
        val backupRootKey = backupRootKeyRepository.getBackupRootKey() ?: return
        pendingRequestStore.add(
            BackupRootKeyPendingRequestEntry(
                request = PendingBackupRootKeyRequest(
                    requestId = content.requestId,
                    requesterClientId = message.senderClientId,
                    requestedAt = DateTimeUtil.currentInstant(),
                    backupRootKeyInfo = backupRootKey.toBackupRootKeyInfo(),
                ),
                conversationId = message.conversationId,
                backupRootKey = backupRootKey,
            )
        )
    }

    private suspend fun handleEnvelope(
        message: Message.Signaling,
        content: MessageContent.BackupRootKeySync.Envelope,
        currentClientId: ClientId,
    ) {
        val backupRootKey = BackupRootKeySyncValidator.validate(content) ?: return
        coordinator.onEnvelope(content.requestId, backupRootKey)
        if (backupRootKeyRepository.getBackupRootKey() == null) {
            backupRootKeyRepository.setBackupRootKey(backupRootKey)
        }
        addPendingMessage(
            message = Message.Signaling(
                id = Uuid.random().toString(),
                content = MessageContent.BackupRootKeySync.Ack(content.requestId, content.keyId),
                conversationId = message.conversationId,
                date = DateTimeUtil.currentInstant(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null,
            ),
            messageTarget = MessageTarget.Client(listOf(Recipient(selfUserId, listOf(message.senderClientId)))),
        )
    }

    private suspend fun addPendingMessage(
        message: Message.Signaling,
        messageTarget: MessageTarget,
    ) {
        pendingMessagesMutex.withLock {
            pendingMessages.add(PendingMessage(message, messageTarget))
        }
    }

    override suspend fun flushPendingMessages() {
        val pending = pendingMessagesMutex.withLock {
            if (pendingMessages.isEmpty()) {
                emptyList()
            } else {
                pendingMessages.toList().also {
                    pendingMessages.clear()
                }
            }
        }

        pending.forEach { (message, target) ->
            messageSender.sendMessage(message, target)
        }
    }

    private data class PendingMessage(
        val message: Message.Signaling,
        val target: MessageTarget,
    )
}
