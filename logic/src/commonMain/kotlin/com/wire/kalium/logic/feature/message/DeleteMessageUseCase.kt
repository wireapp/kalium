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
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class DeleteMessageUseCase(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val assetRepository: AssetRepository,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender,
    private val idMapper: IdMapper
) {

    suspend operator fun invoke(conversationId: ConversationId, messageId: String, deleteForEveryone: Boolean): Either<CoreFailure, Unit> {
        syncManager.startSyncIfIdle()
        val selfUser = userRepository.observeSelfUser().first()

        val generatedMessageUuid = uuid4().toString()
        return clientRepository.currentClientId().flatMap { currentClientId ->
            val message = Message.Regular(
                id = generatedMessageUuid,
                content = if (deleteForEveryone) MessageContent.DeleteMessage(messageId) else MessageContent.DeleteForMe(
                    messageId,
                    conversationId = conversationId.value,
                    qualifiedConversationId = idMapper.toProtoModel(conversationId)
                ),
                conversationId = if (deleteForEveryone) conversationId else selfUser.id,
                date = Clock.System.now().toString(),
                senderUserId = selfUser.id,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited,
            )
            messageSender.sendMessage(message)
        }
            .onSuccess {
                messageRepository.getMessageById(conversationId, messageId)
                    .onSuccess { message ->
                        val assetToRemove = when (message.content) {
                            is MessageContent.Asset -> (message.content as MessageContent.Asset).value.remoteData
                            else -> null
                        }
                        if (assetToRemove != null) {
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
            .flatMap { messageRepository.markMessageAsDeleted(messageId, conversationId) }
            .onFailure {
                kaliumLogger.withFeatureId(MESSAGES).w("delete message failure: $it")
                if (it is CoreFailure.Unknown) {
                    it.rootCause?.printStackTrace()
                }
            }
    }
}
