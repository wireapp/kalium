package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * This singleton allow us to queue ephemeral notifications from different user flows.
 * Ideally we should have logic that allows to mark messages as notified, but this will act for cases when we need to notify the user on
 * information we don't have available persisted.
 */
object EphemeralNotificationsManager {

    private val notifications = Channel<LocalNotificationConversation>(capacity = Channel.CONFLATED)

    fun observeEphemeralNotifications(): Flow<LocalNotificationConversation> {
        return notifications.consumeAsFlow()
    }

    suspend fun scheduleNotification(localNotificationConversation: LocalNotificationConversation) {
        notifications.send(localNotificationConversation)
    }

}
