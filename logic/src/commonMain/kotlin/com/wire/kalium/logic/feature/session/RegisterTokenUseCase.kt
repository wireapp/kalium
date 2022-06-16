package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCode
import com.wire.kalium.network.exceptions.isNotFound

interface RegisterTokenUseCase {
    suspend operator fun invoke(senderId: String, clientId: String): RegisterTokenResult
}

class RegisterTokenUseCaseImpl(
    private val clientRemoteRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository
) : RegisterTokenUseCase {
    override suspend operator fun invoke(senderId: String, clientId: String): RegisterTokenResult =
        notificationTokenRepository.getNotificationToken().fold({
            RegisterTokenResult.Failure.NotificationTokenNotFound(it)
        }, { notificationToken ->
            clientRemoteRepository.registerToken(
                body = PushTokenBody(
                    senderId = senderId, client = clientId, token = notificationToken.token, transport = notificationToken.transport
                )
            ).fold({ failure ->
                RegisterTokenResult.Success
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
                    when {
                        failure.kaliumException.isInvalidCode() -> RegisterTokenResult.Failure.AppNotFound
                        failure.kaliumException.isNotFound() -> RegisterTokenResult.Failure.PushTokenRegister
                        else -> RegisterTokenResult.Failure.Generic(failure)
                    }
                else {
                    RegisterTokenResult.Failure.Generic(failure)
                }
            }, {
                RegisterTokenResult.Success
            })
        })
}


sealed class RegisterTokenResult {
    object Success : RegisterTokenResult()
    sealed class Failure : RegisterTokenResult() {
        object AppNotFound : Failure()
        object PushTokenRegister : Failure()
        class NotificationTokenNotFound(val failure: StorageFailure) : Failure()
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
