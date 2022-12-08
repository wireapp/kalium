package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.util.TimeParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

interface GetNotificationsUseCase {
    suspend operator fun invoke(): Flow<List<LocalNotificationConversation>>
}

/**
 *
 * @param connectionRepository connectionRepository for observing connectionRequests that user should be notified about
 * @param messageRepository MessageRepository for getting Messages that user should be notified about
 * @param userRepository UserRepository for getting SelfUser data, Self userId and OtherUser data (authors of messages)
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
    private val localNotificationMessageMapper: LocalNotificationMessageMapper = MapperProvider.localNotificationMessageMapper()
) : GetNotificationsUseCase {

    @Suppress("LongMethod")
    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> {
        return merge(
            messageRepository.getNotificationMessage(),
            observeConnectionRequests(),
            ephemeralNotificationsManager.observeEphemeralNotifications().map { listOf(it) }
        )
            .distinctUntilChanged()
            .buffer(capacity = 3) // to cover a case when all 3 flows emits at the same time
    }

    private suspend fun observeConnectionRequests(): Flow<List<LocalNotificationConversation>> {
        return connectionRepository.observeConnectionRequestsForNotification()
            .map { requests ->
                requests
                    .filterIsInstance<ConversationDetails.Connection>()
                    .map { localNotificationMessageMapper.fromConnectionToLocalNotificationConversation(it) }
            }
    }

    //TODO: will consider these values in query lever after sqldeight added window functions
    companion object {
        private const val DEFAULT_MESSAGE_LIMIT = 100
        private const val DEFAULT_MESSAGE_OFFSET = 0
        private const val NOTIFICATION_DATE_OFFSET = 1000L
    }
}
