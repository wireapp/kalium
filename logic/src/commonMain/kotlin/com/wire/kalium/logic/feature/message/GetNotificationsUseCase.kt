package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.flatMapFromIterable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.wire.kalium.logic.functional.combine
import com.wire.kalium.logic.util.TimeParser
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface GetNotificationsUseCase {
    suspend operator fun invoke(): Flow<List<LocalNotificationConversation>>
}

class GetNotificationsUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val timeParser: TimeParser,
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper()
) : GetNotificationsUseCase {

    @Suppress("LongMethod")
    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> {
        // Fetching the list of Conversations that have messages to notify user about
        // And SelfUser
        return conversationRepository.getConversationsForNotifications()
            .combine(userRepository.getSelfUser())
            .flatMapLatest { (conversations, selfUser) ->
                if (selfUser.availabilityStatus == UserAvailabilityStatus.AWAY) {
                    // We need to update notifiedData,
                    // to not notify user about that messages, when AvailabilityStatus is changed
                    conversationRepository.updateAllConversationsNotificationDate(timeParser.currentTimeStamp())
                    // If user is AWAY we don't show any notification
                    flowOf(listOf())
                } else {
                    val selfUserId = userRepository.getSelfUserId()
                    conversations.flatMapFromIterable { conversation ->
                        // Fetching the Messages for the Conversation that are newer than `lastNotificationDate`
                        observeMessagesList(conversation)
                            .map { messages ->
                                // Filtering messages according to UserAvailabilityStatus and MutedConversationStatus
                                val eligibleMessages = messages.filterAccordingToStatuses(selfUserId, selfUser, conversation)
                                // If some messages were filtered by status, we need to update lastNotificationDate,
                                // to not notify User about that messages, when User changes status
                                updateConversationNotificationDateIfNeeded(eligibleMessages, messages, conversation)

                                ConversationWithMessages(eligibleMessages, conversation)
                            }
                    }
                }
            }
            // We don't want to display Notification if there is no "new" Messages in Conversation.
            // Sometimes it could happen that Conversation has flag `has_unnotified_messages = true`,
            // but no messages that are younger than `last_notified_message_date`
            // (messages didn't come yet, or user watched it already, etc.)
            .map { it.filter { conversationWithMessages -> conversationWithMessages.messages.isNotEmpty() } }
            .distinctUntilChanged()
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
                                val isOneToOneConversation =
                                    conversationWithMessages.conversation.type == Conversation.Type.ONE_ON_ONE

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

    private suspend fun observeMessagesList(conversation: Conversation) =
        if (conversation.lastNotificationDate == null) {
            // that is a new conversation, lets just fetch last 100 messages for it
            messageRepository.getMessagesForConversation(conversation.id, 100, 0)
        } else {
            messageRepository.getMessagesByConversationAfterDate(conversation.id, conversation.lastNotificationDate)
        }

    private fun getNotificationMessageAuthor(authors: List<OtherUser?>, senderUserId: UserId) =
        publicUserMapper.fromPublicUserToLocalNotificationMessageAuthor(authors.firstOrNull { it?.id == senderUserId })

    private suspend fun updateConversationNotificationDateIfNeeded(
        eligibleMessages: List<Message>,
        messages: List<Message>,
        conversation: Conversation
    ) {
        if (messages.isEmpty()) return

        val newNotificationDate = if (eligibleMessages.isEmpty()) {
            messages.maxOf { it.date }
        } else {
            timeParser.dateMinusMilliseconds(eligibleMessages.minOf { it.date }, 1L)
        }

        conversation.lastNotificationDate.let {
            if (it == null || timeParser.calculateMillisDifference(it, newNotificationDate) > 0)
                conversationRepository.updateConversationNotificationDate(conversation.id, newNotificationDate)
        }
    }

    private fun List<Message>.filterAccordingToStatuses(
        selfUserId: QualifiedID,
        selfUser: SelfUser,
        conversation: Conversation
    ): List<Message> =
        filter { message ->
            (message.senderUserId != selfUserId
                    && isSupportedForNotificationsMessageType(message)
                    && shouldIncludeMessageForNotifications(message, selfUser, conversation.mutedStatus))
        }

    private fun shouldIncludeMessageForNotifications(
        message: Message,
        selfUser: SelfUser,
        conversationMutedStatus: MutedConversationStatus
    ): Boolean =
        when {
            allMuted(conversationMutedStatus, selfUser) -> false
            onlyMentionsAllowed(conversationMutedStatus, selfUser) -> {
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
            allNotificationsAllowed(conversationMutedStatus, selfUser) -> true
            else -> false
        }

    private fun allNotificationsAllowed(conversationMutedStatus: MutedConversationStatus, selfUser: SelfUser) =
        conversationMutedStatus == MutedConversationStatus.AllAllowed
                && (selfUser.availabilityStatus == UserAvailabilityStatus.NONE
                || selfUser.availabilityStatus == UserAvailabilityStatus.AVAILABLE)

    private fun allMuted(conversationMutedStatus: MutedConversationStatus, selfUser: SelfUser) =
        conversationMutedStatus == MutedConversationStatus.AllMuted
                || selfUser.availabilityStatus == UserAvailabilityStatus.AWAY

    private fun onlyMentionsAllowed(conversationMutedStatus: MutedConversationStatus, selfUser: SelfUser) =
        conversationMutedStatus == MutedConversationStatus.OnlyMentionsAllowed
                || selfUser.availabilityStatus == UserAvailabilityStatus.BUSY

    private fun isSupportedForNotificationsMessageType(message: Message): Boolean =
        message.content !is MessageContent.Unknown
                && message.content !is MessageContent.Server
                && message.content !is MessageContent.DeleteMessage
                && message.content !is MessageContent.DeleteForMe

    private data class ConversationWithMessages(val messages: List<Message>, val conversation: Conversation)
}
