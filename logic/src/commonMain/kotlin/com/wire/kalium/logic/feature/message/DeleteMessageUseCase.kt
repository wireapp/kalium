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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.ASSETS
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Deletes a message from the conversation
 */
// todo(interface). extract interface for use case
@Suppress("LongParameterList")
public class DeleteMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val deleteAttachments: DeleteMessageAttachmentsUseCase,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * Operation to delete a message from the conversation
     *
     * @param conversationId the id of the conversation the message belongs to
     * @param messageId the id of the message to delete
     * @param deleteForEveryone either delete the message for everyone or just for the current user
     * @return [MessageOperationResult] indicating success or failure.
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        deleteForEveryone: Boolean,
    ): MessageOperationResult =
        withContext(dispatcher.io) {
            awaitSlowSyncCompletion()

            messageRepository.getMessageById(conversationId, messageId).fold(
                { failure -> MessageOperationResult.Failure(failure) },
                { message ->
                    deleteMessage(message, conversationId, messageId, deleteForEveryone).fold(
                        { MessageOperationResult.Failure(it) },
                        { MessageOperationResult.Success }
                    )
                }
            )
        }

    private suspend fun awaitSlowSyncCompletion() {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
    }

    private suspend fun deleteMessage(
        message: Message,
        conversationId: ConversationId,
        messageId: String,
        deleteForEveryone: Boolean,
    ) = when {
        // Remove a locally queued message without requiring a network signal for Delete for Me.
        message is Message.Regular && message.isSelfMessage &&
            message.status == Message.Status.Pending && message.editStatus == Message.EditStatus.NotEdited && !deleteForEveryone ->
            cancelPendingMessage(conversationId, messageId)

        // TODO: there is a race condition here where a message can still be marked as Message.Status.FAILED but be sent
        // better to send the delete message anyway and let it to other clients to ignore it if the message is not sent
        message.status == Message.Status.Failed -> deleteFailedMessage(conversationId, messageId)
        else -> deletePersistedMessage(message, conversationId, messageId, deleteForEveryone)
    }

    private suspend fun cancelPendingMessage(conversationId: ConversationId, messageId: String) =
        messageSender.cancelPendingMessage(conversationId, messageId).flatMap { cancelled ->
            if (cancelled) {
                Either.Right(Unit)
            } else {
                // Sending won the race. Keep the normal Delete for Me signaling for the sent message.
                messageRepository.getMessageById(conversationId, messageId).flatMap { current ->
                    deletePersistedMessage(current, conversationId, messageId, deleteForEveryone = false)
                }
            }
        }

    private suspend fun deleteFailedMessage(
        conversationId: ConversationId,
        messageId: String,
    ) = messageRepository.deleteMessage(messageId, conversationId)

    private suspend fun deletePersistedMessage(
        message: Message,
        conversationId: ConversationId,
        messageId: String,
        deleteForEveryone: Boolean,
    ) = sendDeleteSignal(conversationId, messageId, deleteForEveryone)
        .onSuccess {
            deleteMessageAsset(message, deleteForEveryone)
        }
        .flatMap {
            persistDeletedMessage(message, conversationId, messageId)
        }
        .onFailure { failure ->
            logDeleteFailure(message, failure)
        }

    private suspend fun sendDeleteSignal(
        conversationId: ConversationId,
        messageId: String,
        deleteForEveryone: Boolean,
    ) = currentClientIdProvider().flatMap { currentClientId ->
        selfConversationIdProvider().flatMap { selfConversationIds ->
            selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                messageSender.sendMessage(
                    createDeleteMessage(
                        messageId = messageId,
                        conversationId = conversationId,
                        selfConversationId = selfConversationId,
                        currentClientId = currentClientId,
                        deleteForEveryone = deleteForEveryone
                    )
                )
            }
        }
    }

    private fun createDeleteMessage(
        messageId: String,
        conversationId: ConversationId,
        selfConversationId: ConversationId,
        currentClientId: ClientId,
        deleteForEveryone: Boolean,
    ) = Message.Signaling(
        id = Uuid.random().toString(),
        content = if (deleteForEveryone) {
            MessageContent.DeleteMessage(messageId)
        } else {
            MessageContent.DeleteForMe(
                messageId,
                conversationId = conversationId
            )
        },
        conversationId = if (deleteForEveryone) conversationId else selfConversationId,
        date = Clock.System.now(),
        senderUserId = selfUserId,
        senderClientId = currentClientId,
        status = Message.Status.Pending,
        isSelfMessage = true,
        expirationData = null
    )

    private suspend fun persistDeletedMessage(
        message: Message,
        conversationId: ConversationId,
        messageId: String,
    ) =
        // In case of ephemeral message, we want to delete it completely from the device, not just mark it deleted.
        // This can only happen when the user decides to delete the message before the self-deletion timer expires.
        if (message is Message.Regular && message.expirationData != null) {
            messageRepository.deleteMessage(messageId, conversationId)
        } else {
            messageRepository.markMessageAsDeleted(messageId, conversationId)
        }

    private fun logDeleteFailure(message: Message, failure: CoreFailure) {
        kaliumLogger.withFeatureId(MESSAGES).w("delete message failure: $message")
        if (failure is CoreFailure.Unknown) {
            failure.rootCause?.printStackTrace()
        }
    }

    private suspend fun deleteMessageAsset(message: Message, deleteForEveryone: Boolean) {
        val assetContent = (message.content as? MessageContent.Asset)?.value
        listOfNotNull(assetContent?.remoteData, assetContent?.preview?.remoteData)
            .distinctBy { it.assetId }
            .forEach { assetToRemove ->

                assetRepository.deleteAsset(
                    assetToRemove.assetId,
                    assetToRemove.assetDomain,
                    assetToRemove.assetToken
                )
                    .onFailure {
                        kaliumLogger.withFeatureId(ASSETS).w("delete message asset failure: $it")
                    }
            }

        // Delete attachments for multipart message
        if (deleteForEveryone && message.content is MessageContent.Multipart) {
            deleteAttachments(message.id, message.conversationId)
        }
    }
}
