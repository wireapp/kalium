package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.api.notification.pushToken.PushTokenRequestBody

class RegisterTokenUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(body: PushTokenRequestBody) = suspending {
        eventRepository.registerToken(body = body)
    }
}
