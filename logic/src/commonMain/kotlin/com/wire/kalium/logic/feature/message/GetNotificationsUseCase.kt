package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.flatMapFromIterable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    @Suppress("LongMethod")
    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> {

        val selfUser = userRepository.getSelfUser().first()

        return conversationRepository.getConversationsForNotifications()
            .flatMapMerge { conversations ->
                // Fetched the list of Conversations that have messages to notify user about
                conversations.flatMapFromIterable { conversation ->
                    val selfUserId = userRepository.getSelfUserId()

                    // Fetching the Messages for the Conversation that are newer than `lastNotificationDate`
                    val messagesListFlow = if (conversation.lastNotificationDate == null) {
                        // that is a new conversation, lets just fetch last 100 messages for it
                        messageRepository.getMessagesByConversationIdAndVisibility(
                            conversation.id,
                            100,
                            0,
                            listOf(Message.Visibility.VISIBLE)
                        )
                    } else {
                        messageRepository.getMessagesByConversationIdAndVisibilityAfterDate(
                            conversation.id,
                            conversation.lastNotificationDate,
                            listOf(Message.Visibility.VISIBLE)
                        )
                    }

                    messagesListFlow
                        .map { messages ->
                            val eligibleMessages = messages
                                .filter { message ->
                                    message.senderUserId != selfUserId
                                            && shouldIncludeMessageForNotifications(message, selfUser, conversation.mutedStatus)
                                }
                            ConversationWithMessages(eligibleMessages, conversation)
                        }
                }
            }
            // We don't want to display Notification if there is no "new" Messages in Conversation.
            // Sometimes it could happen that Conversation has flag `has_unnotified_messages = true`,
            // but no messages that are younger than `last_notified_message_date`
            // (messages didn't come yet, or user watched it already, etc.)
            .map { it.filter { conversationWithMessages -> conversationWithMessages.messages.isNotEmpty() } }
            .flatMapMerge { conversationsWithMessages ->
                // Each message has author and we need to fetch data of each of them.
                // As far as few message could have the same author, we don't want to request it from DB few times.
                // So we create Set of AuthorIDs to fetch them later
                val authorIds = mutableSetOf<UserId>()

                // Filling the authorIds Set
                conversationsWithMessages.forEach { conversationAndMessages ->
                    conversationAndMessages.messages.forEach { authorIds.add(it.senderUserId) }
                }

                // Fetching all the authors by ID
                authorIds
                    .flatMapFromIterable { userId -> userRepository.getKnownUser(userId) }
                    .map { authors ->
                        // Mapping all the fetched data into LocalNotificationConversation to pass it forward
                        conversationsWithMessages
                            .map { conversationWithMessages ->

                                val conversationId = conversationWithMessages.conversation.id
                                val conversationName = conversationWithMessages.conversation.name ?: ""
                                val messages = conversationWithMessages.messages
                                    .map {
                                        val author = getNotificationMessageAuthor(authors, it.senderUserId)
                                        messageMapper.fromMessageToLocalNotificationMessage(it, author)
                                    }

                                val isOneToOneConversation = conversationWithMessages.conversation.type == Conversation.Type.ONE_ON_ONE

                                LocalNotificationConversation(
                                    id = conversationId,
                                    conversationName = conversationName,
                                    messages = messages,
                                    isOneToOneConversation = isOneToOneConversation
                                )
                        }
                    }
            }
            .distinctUntilChanged()
    }

    private fun getNotificationMessageAuthor(authors: List<OtherUser?>, senderUserId: UserId) =
        publicUserMapper.fromPublicUserToLocalNotificationMessageAuthor(authors.firstOrNull { it?.id == senderUserId })

    private fun shouldIncludeMessageForNotifications(
        message: Message,
        selfUser: SelfUser,
        conversationMutedStatus: MutedConversationStatus
    ): Boolean =
        when {
            message is Message.Server -> false
            message.visibility != Message.Visibility.VISIBLE -> false
            else -> when (conversationMutedStatus) {
                MutedConversationStatus.AllAllowed -> true
                MutedConversationStatus.OnlyMentionsAllowed -> {
                    when (val content = message.content) {
                        is MessageContent.Text -> {
                            val containsSelfUserName = selfUser.name?.let { selfUsername ->
                                content.value.contains("@$selfUsername")
                            } ?: false
                            val containsSelfHandle = selfUser.handle?.let { selfHandle ->
                                content.value.contains("@$selfHandle")
                            } ?: false

                            containsSelfUserName or containsSelfHandle
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }

    private data class ConversationWithMessages(val messages: List<Message>, val conversation: Conversation)
}
