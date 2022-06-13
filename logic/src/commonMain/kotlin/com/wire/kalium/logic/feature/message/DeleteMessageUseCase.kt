package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class DeleteMessageUseCase(
    private val messageRepository: MessageRepository,
    private val selfUserRepository: SelfUserRepository,
    private val clientRepository: ClientRepository,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender,
    private val idMapper: IdMapper
) {

    suspend operator fun invoke(conversationId: ConversationId, messageId: String, deleteForEveryone: Boolean): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()
        val selfUser = selfUserRepository.getSelfUser().first()

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
        }.flatMap {
            messageRepository.deleteMessage(messageId, conversationId)
        }.onFailure {
            kaliumLogger.w("delete message failure: $it")
            if (it is CoreFailure.Unknown) {
                it.rootCause?.printStackTrace()
            }
        }
    }
}
