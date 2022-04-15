package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class SendTextMessageUseCase(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender,
) {

    suspend operator fun invoke(conversationId: ConversationId, text: String): SendTextMessageResult {
        syncManager.waitForSlowSyncToComplete()
        val selfUser = userRepository.getSelfUser().first()
        val generatedMessageUuid = uuid4().toString()

        return suspending {
            clientRepository.currentClientId().map { currentClientId ->
                Message(
                    id = generatedMessageUuid,
                    content = MessageContent.Text(text),
                    conversationId = conversationId,
                    date = Clock.System.now().toString(),
                    senderUserId = selfUser.id,
                    senderClientId = currentClientId,
                    status = Message.Status.PENDING
                )
            }.flatMap { message ->
                messageRepository.persistMessage(message)
                    .flatMap {
                        messageSender.trySendingOutgoingMessageById(conversationId, generatedMessageUuid)
                    }.flatMap {
                        messageRepository.persistMessage(message.copy(status = Message.Status.SENT))
                    }
            }.onFailure { SendTextMessageResult.Failure("test") }
                .onSuccess { SendTextMessageResult.Success }
                .coFold(
                    { SendTextMessageResult.Failure("test") },
                    { SendTextMessageResult.Success }
                )
        }
    }
}

sealed class SendTextMessageResult {
    object Success : SendTextMessageResult()
    data class Failure(val messageId: String) : SendTextMessageResult()
}
