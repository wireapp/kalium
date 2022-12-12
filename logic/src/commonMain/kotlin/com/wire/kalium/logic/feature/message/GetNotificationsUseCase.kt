package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

interface GetNotificationsUseCase {
    suspend operator fun invoke(): Flow<List<LocalNotificationConversation>>
}

/**
 *
 * @param connectionRepository connectionRepository for observing connectionRequests that user should be notified about
 * @param messageRepository MessageRepository for getting Messages that user should be notified about
 * @param userRepository UserRepository for getting SelfUser data with [UserAvailabilityStatus]
 * @param conversationRepository ConversationRepository for getting conversations that have messages that user should be notified about
 * @param timeParser TimeParser for getting current time as a formatted String and making some calculation on String TimeStamp
 * @param messageMapper MessageMapper for mapping Message object into LocalNotificationMessage
 * @param localNotificationMessageMapper LocalNotificationMessageMapper for mapping PublicUser object into LocalNotificationMessageAuthor
 *
 * @return Flow<List<LocalNotificationConversation>> - Flow of Notification List that should be shown to the user.
 * That Flow emits everytime when the list is changed
 */
@Suppress("LongParameterList")
internal class GetNotificationsUseCaseImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val timeParser: TimeParser,
    private val ephemeralNotificationsManager: EphemeralNotificationsMgr,
    private val selfUserId: UserId,
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId),
    private val localNotificationMessageMapper: LocalNotificationMessageMapper = MapperProvider.localNotificationMessageMapper(),
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : GetNotificationsUseCase {

    @Suppress("LongMethod")
    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> {
        return combine(merge(
            messageRepository.getNotificationMessage(),
            observeConnectionRequests(),
            ephemeralNotificationsManager.observeEphemeralNotifications().map { listOf(it) }
        )
            .distinctUntilChanged(),
            userRepository.observeSelfUser().map { it.availabilityStatus }.distinctUntilChanged()
        ) { notifications, selfStatus ->
            when (selfStatus) {
                UserAvailabilityStatus.NONE -> notifications
                UserAvailabilityStatus.AVAILABLE -> notifications
                UserAvailabilityStatus.BUSY -> notifications.map { it.copy(messages = it.messages.filter {notification ->
                    when(notification) {
                        is LocalNotificationMessage.Comment -> false
                        is LocalNotificationMessage.ConnectionRequest -> false
                        is LocalNotificationMessage.ConversationDeleted -> false
                        is LocalNotificationMessage.Text -> notification.isMentionedSelf || notification.isQuotingSelfUser
                    }
                }) }.filter { it.messages.isEmpty() }
                UserAvailabilityStatus.AWAY -> emptyList()
            }
        }
            .buffer(capacity = 3) // to cover a case when all 3 flows emits at the same time
            .flowOn(dispatcher.io)
    }

    private suspend fun observeConnectionRequests(): Flow<List<LocalNotificationConversation>> {
        return connectionRepository.observeConnectionRequestsForNotification()
            .map { requests ->
                requests
                    .filterIsInstance<ConversationDetails.Connection>()
                    .map { localNotificationMessageMapper.fromConnectionToLocalNotificationConversation(it) }
            }
    }

    // TODO: will consider these values in query lever after SQLDelight added window functions
    companion object {
        private const val DEFAULT_MESSAGE_LIMIT = 100
        private const val DEFAULT_MESSAGE_OFFSET = 0
        private const val NOTIFICATION_DATE_OFFSET = 1000L
    }
}
