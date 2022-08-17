package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.ASSETS
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class DeleteMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val assetRepository: AssetRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender
) {

    suspend operator fun invoke(conversationId: ConversationId, messageId: String, deleteForEveryone: Boolean): Either<CoreFailure, Unit> {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        return messageRepository.getMessageById(conversationId, messageId).map { message ->
            when (message.status) {
                Message.Status.FAILED -> messageRepository.deleteMessage(messageId, conversationId)
                else -> {
                    val selfUser = userRepository.observeSelfUser().first()
                    val generatedMessageUuid = uuid4().toString()
                    return clientRepository.currentClientId().flatMap { currentClientId ->
                        val regularMessage = Message.Regular(
                            id = generatedMessageUuid,
                            content = if (deleteForEveryone) MessageContent.DeleteMessage(messageId) else MessageContent.DeleteForMe(
                                messageId,
                                unqualifiedConversationId = conversationId.value,
                                conversationId = conversationId
                            ),
                            conversationId = if (deleteForEveryone) conversationId else selfUser.id,
                            date = Clock.System.now().toString(),
                            senderUserId = selfUser.id,
                            senderClientId = currentClientId,
                            status = Message.Status.PENDING,
                            editStatus = Message.EditStatus.NotEdited,
                        )
                        messageSender.sendMessage(regularMessage)
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
                AssetId(assetToRemove.assetId, assetToRemove.assetDomain.orEmpty()),
                assetToRemove.assetToken
            )
                .onFailure {
                    kaliumLogger.withFeatureId(ASSETS).w("delete message asset failure: $it")
                }
        }
    }
}
