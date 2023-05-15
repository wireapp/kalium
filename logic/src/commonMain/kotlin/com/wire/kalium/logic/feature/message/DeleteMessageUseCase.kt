/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.ASSETS
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.first

/**
 * Deletes a message from the conversation
 */
@Suppress("LongParameterList")
class DeleteMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfConversationIdProvider: SelfConversationIdProvider
) {

    /**
     * Operation to delete a message from the conversation
     *
     * @param conversationId the id of the conversation the message belongs to
     * @param messageId the id of the message to delete
     * @param deleteForEveryone either delete the message for everyone or just for the current user
     * @return [Either] [CoreFailure] or [Unit] //fixme: we should not return [Either]
     */
    suspend operator fun invoke(conversationId: ConversationId, messageId: String, deleteForEveryone: Boolean): Either<CoreFailure, Unit> {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        return messageRepository.getMessageById(conversationId, messageId).map { message ->
            when (message.status) {
                // TODO: there is a race condition here where a message can still be marked as Message.Status.FAILED but be sent
                // better to send the delete message anyway and let it to other clients to ignore it if the message is not sent
                Message.Status.FAILED -> messageRepository.deleteMessage(messageId, conversationId)
                else -> {
                    return currentClientIdProvider().flatMap { currentClientId ->
                        selfConversationIdProvider().flatMap { selfConversationIds ->
                            selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                                val regularMessage = Message.Signaling(
                                    id = uuid4().toString(),
                                    content = if (deleteForEveryone) MessageContent.DeleteMessage(messageId) else
                                        MessageContent.DeleteForMe(
                                            messageId,
                                            conversationId = conversationId
                                        ),
                                    conversationId = if (deleteForEveryone) conversationId else selfConversationId,
                                    date = DateTimeUtil.currentIsoDateTimeString(),
                                    senderUserId = selfUserId,
                                    senderClientId = currentClientId,
                                    status = Message.Status.PENDING,
                                    isSelfMessage = true
                                )
                                messageSender.sendMessage(regularMessage)
                            }
                        }
                    }
                        .onSuccess { deleteMessageAsset(message) }
                        .flatMap {
                            // in case of ephemeral message, we want to delete it completely from the device, not just mark it as deleted
                            // as this can only happen when the user decides to delete the message, before the self-deletion timer expired
                            val isEphemeralMessage = message is Message.Regular && message.expirationData != null
                            if (isEphemeralMessage) {
                                messageRepository.deleteMessage(messageId, conversationId)
                            } else {
                                messageRepository.markMessageAsDeleted(messageId, conversationId)
                            }
                        }
                        .onFailure { failure ->
                            kaliumLogger.withFeatureId(MESSAGES).w("delete message failure: $message")
                            if (failure is CoreFailure.Unknown) {
                                failure.rootCause?.printStackTrace()
                            }
                        }
                }
            }
        }
    }

    private suspend fun deleteMessageAsset(message: Message) {
        (message.content as? MessageContent.Asset)?.value?.remoteData?.let { assetToRemove ->

            assetRepository.deleteAsset(
                assetToRemove.assetId,
                assetToRemove.assetDomain,
                assetToRemove.assetToken
            )
                .onFailure {
                    kaliumLogger.withFeatureId(ASSETS).w("delete message asset failure: $it")
                }
        }
    }
}
