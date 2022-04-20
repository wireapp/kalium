package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.api.notification.pushToken.PushTokenRequestBody

class PushFCMTokenUseCase(
    private val eventRepository: EventRepository,
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(body: PushTokenRequestBody) = suspending {
        clientRepository.currentClientId().onSuccess { clintId ->
            eventRepository.registerFCMToken(body = body)
        }
    }
}
