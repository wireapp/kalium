package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * This singleton allow us to queue ephemeral notifications from different user flows.
 * Ideally we should have logic that allows to mark messages as notified, but this will act for cases when we need to notify the user on
 * information we have not persisted or that is not available anymore.
 */
object EphemeralNotificationsManager: EphemeralNotifications {

    private val notifications =
        Channel<LocalNotificationConversation>(capacity = Channel.CONFLATED) { emptyFlow<LocalNotificationConversation>() }

    override suspend fun observeEphemeralNotifications(): Flow<LocalNotificationConversation> {
        return notifications.consumeAsFlow()
    }

    override suspend fun scheduleNotification(localNotificationConversation: LocalNotificationConversation) {
        notifications.send(localNotificationConversation)
    }

}

interface EphemeralNotifications {
    suspend fun observeEphemeralNotifications(): Flow<LocalNotificationConversation>
    suspend fun scheduleNotification(localNotificationConversation: LocalNotificationConversation)
}
