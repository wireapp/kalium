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
package com.wire.kalium.logic.feature.message.ephemeral

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.ASSETS
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mockable
import kotlinx.datetime.Clock

/**
 * When the self user is receiver of the self deletion message,
 * we delete it permanently after expiration and inform the sender by broadcasting a message to delete
 * for the self-deleting message, before the receiver does it on the sender side, the message is simply marked as deleted
 * see [com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl]
 **/
@Mockable
internal interface DeleteEphemeralMessageForSelfUserAsReceiverUseCase {
    /**
     * @param conversationId the conversation id that contains the self-deleting message
     * @param messageId the id of the self-deleting message
     */
    suspend operator fun invoke(conversationId: ConversationId, messageId: String): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val syncManager: SyncManager,
) : DeleteEphemeralMessageForSelfUserAsReceiverUseCase {

    override suspend fun invoke(conversationId: ConversationId, messageId: String): Either<CoreFailure, Unit> =
        messageRepository.getMessageById(conversationId, messageId)
            .onSuccess { message ->
                deleteMessageAssetLocallyIfExists(message)
            }
            .flatMap { message ->
                messageRepository.markMessageAsDeleted(messageId, conversationId)
                    .flatMap {
                        currentClientIdProvider()
                            .flatMap { currentClientId ->
                                sendDeleteMessageToSelf(
                                    message.id,
                                    conversationId,
                                    currentClientId
                                ).flatMap {

                                    // Wait until the sync is complete to avoid sending message with
                                    // potentially invalid epoch
                                    syncManager.waitUntilLive()

                                    sendDeleteMessageToOriginalSender(
                                        message.id,
                                        message.conversationId,
                                        message.senderUserId,
                                        currentClientId
                                    )
                                }.flatMap {
                                    messageRepository.deleteMessage(messageId, conversationId)
                                }
                            }
                    }
            }

    private suspend fun sendDeleteMessageToSelf(
        messageToDelete: String,
        conversationId: ConversationId,
        currentClientId: ClientId
    ): Either<CoreFailure, Unit> = selfConversationIdProvider().flatMap { selfConversaionIdList ->
        selfConversaionIdList.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
            Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.DeleteForMe(messageToDelete, conversationId),
                conversationId = selfConversationId,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null
            ).let { deleteSignalingMessage ->
                messageSender.sendMessage(deleteSignalingMessage, MessageTarget.Conversation())
            }
        }
    }

    private suspend fun sendDeleteMessageToOriginalSender(
        messageToDelete: String,
        conversationId: ConversationId,
        originalMessageSender: UserId,
        currentClientId: ClientId
    ) = Message.Signaling(
        id = uuid4().toString(),
        content = MessageContent.DeleteMessage(messageToDelete),
        conversationId = conversationId,
        date = Clock.System.now(),
        senderUserId = selfUserId,
        senderClientId = currentClientId,
        status = Message.Status.Pending,
        isSelfMessage = true,
        expirationData = null
    ).let { deleteSignalingMessage ->
        messageSender.sendMessage(
            deleteSignalingMessage,
            MessageTarget.Users(userId = listOf(originalMessageSender))
        )
    }

    private suspend fun deleteMessageAssetLocallyIfExists(message: Message) {
        (message.content as? MessageContent.Asset)?.value?.remoteData?.let { assetToRemove ->
            assetRepository.deleteAssetLocally(assetToRemove.assetId).onFailure {
                kaliumLogger.withFeatureId(ASSETS).w("delete message asset failure: $it")
            }
        }
    }
}
