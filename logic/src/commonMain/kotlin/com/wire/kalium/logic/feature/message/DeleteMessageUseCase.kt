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
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class DeleteMessageUseCase(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender
) {

    suspend operator fun invoke(conversationId: ConversationId, messageId: String, deleteForEveryone: Boolean): Either<CoreFailure, Unit> {
        syncManager.waitForSlowSyncToComplete()
        val selfUser = userRepository.getSelfUser().first()

        return suspending {
            val generatedMessageUuid = uuid4().toString()

            clientRepository.currentClientId().flatMap { currentClientId ->
                val message = Message(
                    id = generatedMessageUuid,
                    content = if (deleteForEveryone)
                        MessageContent.DeleteMessage(messageId)
                    else
                        //set the conversationId as self
                        MessageContent.DeleteForMe(messageId, conversationId),
                    conversationId = conversationId,
                    date = Clock.System.now().toString(),
                    senderUserId = selfUser.id,
                    senderClientId = currentClientId,
                    status = Message.Status.PENDING
                )
                messageSender.getRecipientsAndAttemptSend(conversationId, message)
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
}
