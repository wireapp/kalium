package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.network.api.notification.pushToken.PushTokenRequestBody
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCode

class RegisterTokenUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(senderId: String, clientId: String, token: String, transport: String): RegisterTokenResult =
        eventRepository.registerToken(
            body = PushTokenRequestBody(
                senderId = senderId, client =clientId, token =token, transport =transport
            )
        ).fold({
            if (
                it is NetworkFailure.ServerMiscommunication &&
                it.kaliumException is KaliumException.InvalidRequestError &&
                it.kaliumException.isInvalidCode()
            ) {
                RegisterTokenResult.Failure.AppNotFound
            } else {
                RegisterTokenResult.Failure.Generic(it)
            }
        }, {
            RegisterTokenResult.Success
        })
}


sealed class RegisterTokenResult {
    object Success : RegisterTokenResult()
    sealed class Failure : RegisterTokenResult() {
        object AppNotFound : Failure()
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
