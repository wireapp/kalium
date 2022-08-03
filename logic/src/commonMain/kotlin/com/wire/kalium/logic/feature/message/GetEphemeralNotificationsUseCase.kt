package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

interface GetEphemeralNotificationsUseCase {
    suspend operator fun invoke(): Flow<LocalNotificationConversation>
}

class GetEphemeralNotificationsUseCaseImpl : GetEphemeralNotificationsUseCase {

    private val notifications = Channel<LocalNotificationConversation>(capacity = Channel.CONFLATED)

    override suspend fun invoke(): Flow<LocalNotificationConversation> {
        return notifications.consumeAsFlow()
    }

    internal suspend fun scheduleNotification(localNotificationConversation: LocalNotificationConversation) {
        notifications.send(localNotificationConversation)
    }

}
