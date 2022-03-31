package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class SendTextMessageUseCase(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender
) {

    suspend operator fun invoke(conversationId: ConversationId, text: String): Either<CoreFailure, Unit> {
        syncManager.waitForSlowSyncToComplete()
        val selfUser = userRepository.getSelfUser().first()

        return suspending {
            val generatedMessageUuid = uuid4().toString()

            clientRepository.currentClientId().flatMap { currentClientId ->
                val message = Message(
                    id = generatedMessageUuid,
                    content = MessageContent.Text(text),
                    contentType = Message.ContentType.ASSET,
                    conversationId = conversationId,
                    date = Clock.System.now().toString(),
                    senderUserId = selfUser.id,
                    senderClientId = currentClientId,
                    status = Message.Status.PENDING
                )
                messageRepository.persistMessage(message)
            }.flatMap {
                messageSender.trySendingOutgoingMessage(conversationId, generatedMessageUuid)
            }.onFailure {
                println(it)
                if(it is CoreFailure.Unknown){
                    //TODO Did I write multiplatform logging today?
                    it.rootCause?.printStackTrace()
                }
            }
        }
    }
}
