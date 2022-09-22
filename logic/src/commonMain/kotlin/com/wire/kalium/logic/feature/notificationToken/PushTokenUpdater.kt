package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal class PushTokenUpdater(
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val pushTokenRepository: PushTokenRepository,
) {

    suspend fun monitorTokenChanges() {
        pushTokenRepository.observeUpdateFirebaseTokenFlag()
            .filter { it }
            .flatMapLatest { clientRepository.observeCurrentClientId().distinctUntilChanged() }
            .filterNotNull()
            .map { clientId ->
                notificationTokenRepository.getNotificationToken()
                    .flatMap { notificationToken ->
                        clientRepository.registerToken(
                            body = PushTokenBody(
                                senderId = notificationToken.applicationId,
                                client = clientId.value,
                                token = notificationToken.token,
                                transport = notificationToken.transport
                            )
                        )
                    }
            }
            .collect { result ->
                if (result.isRight()) pushTokenRepository.setUpdateFirebaseTokenFlag(false)
            }
    }
}
