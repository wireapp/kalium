package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal interface ObserveNotificationTokenUpdating {
    suspend operator fun invoke()
}

internal class ObserveNotificationTokenUpdatingImpl(
    private val clientRemoteRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val userRepository: UserRepository,
) : ObserveNotificationTokenUpdating {

    override suspend operator fun invoke() {
        userRepository.observeUpdateFirebaseTokenFlag()
            .filter { it }
            .flatMapLatest { clientRemoteRepository.observeCurrentClientId() }
            .filterNotNull()
            .map { clientId ->
                notificationTokenRepository.getNotificationToken()
                    .flatMap { notificationToken ->
                        clientRemoteRepository.registerToken(
                            body = PushTokenBody(
                                senderId = notificationToken.senderId,
                                client = clientId.value,
                                token = notificationToken.token,
                                transport = notificationToken.transport
                            )
                        )
                    }
            }
            .collect { result ->
                if (result.isRight()) userRepository.setUpdateFirebaseTokenFlag(false)
            }
    }
}
