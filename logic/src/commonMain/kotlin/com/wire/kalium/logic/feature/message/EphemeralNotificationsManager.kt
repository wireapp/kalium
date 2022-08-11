package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.di.MapperProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * This singleton allow us to queue ephemeral notifications from different user flows.
 * Ideally we should have logic that allows to mark messages as notified, but this will act for cases when we need to notify the user on
 * information we have not persisted or that is not available anymore.
 */
object EphemeralNotificationsManager : EphemeralNotificationsMgr {

    private val mapper by lazy { MapperProvider.localNotificationMessageMapper() }

    private val notifications =
        Channel<LocalNotificationConversation>(capacity = Channel.CONFLATED) { emptyFlow<LocalNotificationConversation>() }

    override suspend fun observeEphemeralNotifications(): Flow<LocalNotificationConversation> {
        return notifications.consumeAsFlow()
    }

    override suspend fun scheduleNotification(ephemeralConversationNotification: EphemeralConversationNotification) {
        val localNotificationConversation = mapper.fromConversationEventToLocalNotification(
            ephemeralConversationNotification.conversationEvent,
            ephemeralConversationNotification.conversation,
            ephemeralConversationNotification.user
        )
        notifications.send(localNotificationConversation)
    }

}

interface EphemeralNotificationsMgr {
    suspend fun observeEphemeralNotifications(): Flow<LocalNotificationConversation>
    suspend fun scheduleNotification(ephemeralConversationNotification: EphemeralConversationNotification)
}

/**
 * Class to pass some data to this manager and later being able to map it to the correct types.
 * We can expand this class later when we have more cases for ephemeral notifications.
 */
data class EphemeralConversationNotification(
    val conversationEvent: Event.Conversation,
    val conversation: Conversation,
    val user: User?
)
