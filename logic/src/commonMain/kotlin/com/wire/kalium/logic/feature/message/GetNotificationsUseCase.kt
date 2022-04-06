package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.DbNotificationConversation
import com.wire.kalium.logic.data.notification.DbNotificationMessage
import com.wire.kalium.logic.data.notification.DbNotificationMessageAuthor
import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class GetNotificationsUseCase(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository
) {

    suspend operator fun invoke(): Flow<List<Either<StorageFailure, DbNotificationConversation>>> {
        return messageRepository.getMessagesForNotification()
            .flatMapMerge { dbMessages ->
                val cacheMap: Map<QualifiedID, PublicUser?> = mapOf()
                flatMapFromIterable(dbMessages) { msg ->
                    //to not request the same User few times
                    val cachedPublicUser = cacheMap[msg.senderUserId]

                    if (cachedPublicUser == null) {
                        userRepository.getKnownUser(msg.senderUserId)
                            .map {
                                cacheMap.plus(msg.senderUserId to it)
                                MessageWithSenderData(msg, it)
                            }
                    } else {
                        flow { emit(MessageWithSenderData(msg, cachedPublicUser)) }
                    }
                }
            }
            .map { it.groupBy { data -> data.message.conversationId } }
            .flatMapMerge { map ->
                flatMapFromIterable(map.keys) { conversationId ->
                    conversationRepository.getConversationDetails(conversationId)
                        .fold({ flow { emit(Either.Left(it) as Either<StorageFailure, DbNotificationConversation>) } }) { conversationFlow ->
                            conversationFlow.map { conversation ->

                                val messagesWithUser = map[conversationId]
                                val conversationName = conversation.name ?: ""

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

                                val isOneToOneConversation = conversation.type == Conversation.Type.ONE_ON_ONE

                                Either.Right(
                                    DbNotificationConversation(
                                        id = conversationId,
                                        name = conversationName,
                                        messages = messages,
                                        isOneToOneConversation = isOneToOneConversation
                                    )
                                ) as Either<StorageFailure, DbNotificationConversation>
                            }
                        }
                }
            }
    }

    private data class MessageWithSenderData(val message: Message, val user: PublicUser?)
}

private suspend fun <A, B> flatMapFromIterable(
    collectionA: Collection<A>,
    block: suspend (A) -> Flow<B>
): Flow<List<B>> {
    return flow {
        val result = mutableListOf<B>()
        collectionA.forEach { a ->
            block(a).collect { b -> result.add(b) }
        }
        emit(result)
    }
}
