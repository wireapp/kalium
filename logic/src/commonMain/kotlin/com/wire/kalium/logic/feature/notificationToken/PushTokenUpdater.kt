package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

internal class PushTokenUpdater(
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val pushTokenRepository: PushTokenRepository,
) {

    suspend fun monitorTokenChanges() {
        pushTokenRepository.observeUpdateFirebaseTokenFlag()
            .filter {
                println("cyka should update token: $it")
                it
            }
            .flatMapLatest { clientRepository.observeCurrentClientId().distinctUntilChanged() }
            .onEach { println("cyka clientId got $it") }
            .filterNotNull()
            .map { clientId ->
                notificationTokenRepository.getNotificationToken()
                    .onFailure { println("cyka getNotificationToken error: $it") }
                    .flatMap { notificationToken ->
                        println("cyka registering token $notificationToken")
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
                println("cyka registering result ${result.isRight()}")
                result.fold({ println("cyka error: $it") }) {}
                if (result.isRight()) pushTokenRepository.setUpdateFirebaseTokenFlag(false)
            }
    }
}
