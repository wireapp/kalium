package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

/**
 * Get notifications for the current user
 */
interface GetNotificationsUseCase {
    /**
     * Operation to get all notifications, the Flow emits everytime when the list is changed
     * @return [Flow] of [List] of [LocalNotificationConversation] with the List that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<LocalNotificationConversation>>
}

/**
 *
 * @param connectionRepository connectionRepository for observing connectionRequests that user should be notified about
 * @param messageRepository MessageRepository for getting Messages that user should be notified about
 * @param localNotificationMessageMapper LocalNotificationMessageMapper for mapping PublicUser object into LocalNotificationMessageAuthor
 */
@Suppress("LongParameterList")
internal class GetNotificationsUseCaseImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val messageRepository: MessageRepository,
    private val ephemeralNotificationsManager: EphemeralNotificationsMgr,
    private val localNotificationMessageMapper: LocalNotificationMessageMapper = MapperProvider.localNotificationMessageMapper(),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetNotificationsUseCase {

    @Suppress("LongMethod")
    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> = withContext(dispatchers.default) {
        merge(
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
}
