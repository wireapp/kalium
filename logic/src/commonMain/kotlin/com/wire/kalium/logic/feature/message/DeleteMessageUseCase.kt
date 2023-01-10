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
                                )
                                messageSender.sendMessage(regularMessage)
                            }
                        }
                    }
                        .onSuccess { deleteMessageAsset(message) }
                        .flatMap { messageRepository.markMessageAsDeleted(messageId, conversationId) }
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
