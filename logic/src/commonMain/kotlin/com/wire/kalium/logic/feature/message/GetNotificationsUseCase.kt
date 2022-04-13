package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.flatMapFromIterable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

interface GetNotificationsUseCase {
    suspend operator fun invoke(): Flow<List<LocalNotificationConversation>>
}

class GetNotificationsUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository
) : GetNotificationsUseCase {

    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> {
        return conversationRepository.getConversationsForNotifications()
            .flatMapMerge { conversations ->
                conversations.flatMapFromIterable { conversation ->
                    messageRepository.getMessagesByConversationAndDate(conversation.id, conversation.lastNotificationDate ?: "")
                        .map { messages -> ConversationWithMessages(messages, conversation) }
                }
            }
            .map { it.filter { conversationWithMessages -> conversationWithMessages.messages.isNotEmpty() } }
            .flatMapMerge { conversationsWithMessages ->
                val authorIds = mutableSetOf<UserId>()

                conversationsWithMessages.forEach { conversationAndMessages ->
                    conversationAndMessages.messages.forEach { authorIds.add(it.senderUserId) }
                }

                authorIds.flatMapFromIterable { userId -> userRepository.getKnownUser(userId) }
                    .map { authors ->
                        conversationsWithMessages.map { conversationWithMessages ->
                            val conversationId = conversationWithMessages.conversation.id
                            val conversationName = conversationWithMessages.conversation.name ?: ""
                            val messages = conversationWithMessages.messages.map { it.toDbNotificationMessage(authors) }
                            val isOneToOneConversation = conversationWithMessages.conversation.type == Conversation.Type.ONE_ON_ONE

                            LocalNotificationConversation(
                                id = conversationId,
                                name = conversationName,
                                messages = messages,
                                isOneToOneConversation = isOneToOneConversation
                            )
                        }
                    }
            }
    }

    private fun Message.toDbNotificationMessage(authors: List<OtherUser?>): LocalNotificationMessage {
        val author = getNotificationMessageAuthor(authors, senderUserId)
        val time = date

        return when (content) {
            is MessageContent.Text -> LocalNotificationMessage.Text(author, time, content.value)
            else -> LocalNotificationMessage.Text(author, time, "Something not a text") //TODO
        }
    }

    private fun getNotificationMessageAuthor(authors: List<OtherUser?>, senderUserId: UserId) =
        LocalNotificationMessageAuthor(authors.firstOrNull { it?.id == senderUserId }?.name ?: "", null)

    private data class ConversationWithMessages(val messages: List<Message>, val conversation: Conversation)
}
