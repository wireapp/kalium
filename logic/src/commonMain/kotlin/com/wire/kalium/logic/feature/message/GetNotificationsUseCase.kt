package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.DbNotificationConversation
import com.wire.kalium.logic.data.notification.DbNotificationMessage
import com.wire.kalium.logic.data.notification.DbNotificationMessageAuthor
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class GetNotificationsUseCase(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository
) {

    suspend operator fun invoke(): Flow<List<DbNotificationConversation>> {
        return messageRepository.getMessagesForNotification()
            .flatMapMerge { dbMessages ->
                val authorIds = dbMessages.map { it.senderUserId }.toSet()

                flatMapFromIterable(authorIds) { userId -> userRepository.getKnownUser(userId) }
                    .map { authors ->
                        dbMessages.map { msg ->
                            MessageWithSenderData(msg, authors.firstOrNull { it?.id == msg.senderUserId })
                        }
                    }
            }
            .map { it.groupBy { data -> data.message.conversationId } }
            .flatMapMerge { map ->
                flatMapFromIterable(map.keys) { conversationId ->
                    conversationRepository.getConversationDetailsById(conversationId)
                        .map { details ->
                            val messagesWithUser = map[conversationId]
                            val conversationName = details.conversation.name ?: ""

                            val messages: List<DbNotificationMessage> = messagesWithUser?.map { msg ->
                                val author = DbNotificationMessageAuthor(msg.user?.name ?: "", null)
                                val time = msg.message.date

                                when (msg.message.content) {
                                    is MessageContent.Text -> {
                                        val text = (msg.message.content as MessageContent.Text).value
                                        DbNotificationMessage.Text(author, time, text)
                                    }
                                    else -> {
                                        //TODO
                                        DbNotificationMessage.Text(author, time, "Something not a text")
                                    }
                                }
                            } ?: listOf()

                            val isOneToOneConversation = details.conversation.type == Conversation.Type.ONE_ON_ONE

                            DbNotificationConversation(
                                id = conversationId,
                                name = conversationName,
                                messages = messages,
                                isOneToOneConversation = isOneToOneConversation
                            )
                        }
                }
            }
    }

    private data class MessageWithSenderData(val message: Message, val user: OtherUser?)
}

private suspend fun <A, B> flatMapFromIterable(
    collectionA: Collection<A>,
    block: suspend (A) -> Flow<B>
): Flow<List<B>> {
    return flow {
        val result = mutableListOf<B>()
        collectionA.forEach { a ->
            block(a)
                .take(1)
                .collect { b ->
                    result.add(b)
                    if (result.size == collectionA.size) emit(result)
                }
        }
    }
}
