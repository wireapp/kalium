package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.flatMapFromIterable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map

interface GetNotificationsUseCase {
    suspend operator fun invoke(): Flow<List<LocalNotificationConversation>>
}

class GetNotificationsUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper()
) : GetNotificationsUseCase {

    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> {
        return conversationRepository.getConversationsForNotifications()
            .flatMapMerge { conversations ->
                conversations.flatMapFromIterable { conversation ->
                    val selfUserId = userRepository.getSelfUserId()

                    messageRepository.getMessagesByConversationAndDate(conversation.id, conversation.lastNotificationDate ?: "")
                        .map { messages ->
                            val messagesWithoutMy = messages.filter { msg -> msg.senderUserId != selfUserId }
                            ConversationWithMessages(messagesWithoutMy, conversation)
                        }
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
                            val messages = conversationWithMessages.messages.map {
                                val author = getNotificationMessageAuthor(authors, it.senderUserId)
                                messageMapper.fromMessageToLocalNotificationMessage(it, author)
                            }
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

    private fun getNotificationMessageAuthor(authors: List<OtherUser?>, senderUserId: UserId) =
        publicUserMapper.fromPublicUserToLocalNotificationMessageAuthor(authors.firstOrNull { it?.id == senderUserId })

    private data class ConversationWithMessages(val messages: List<Message>, val conversation: Conversation)
}
