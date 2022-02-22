package com.wire.kalium.logic.feature.message

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

class SendTextMessageUseCase(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val syncManager: SyncManager
) {

    suspend operator fun invoke(conversationId: ConversationId, text: String): Either<CoreFailure, Unit> {
        syncManager.waitForSlowSyncToComplete()
        val selfUser = userRepository.getSelfUser().first()

        return suspending {
            clientRepository.currentClientId().flatMap { currentClientId ->

                val message = Message(
                    id = "someUUID",
                    content = MessageContent.Text(text),
                    conversationId = conversationId,
                    date = "25 Jan 2022 13:30:00 GMT",
                    senderUserId = selfUser.id,
                    senderClientId = currentClientId,
                    status = Message.Status.PENDING
                )
                messageRepository.persistMessage(message)
            }
        }
    }
}
