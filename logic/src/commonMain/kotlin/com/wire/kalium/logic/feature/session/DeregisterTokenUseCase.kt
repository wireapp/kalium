package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNotFound

interface DeregisterTokenUseCase {
    suspend operator fun invoke(): Result

    sealed class Result {
        object Success : Result()
        sealed class Failure : Result() {
            object NotFound : Failure()
            data class Generic(val coreFailure: CoreFailure) : Failure()
        }
    }
}

class DeregisterTokenUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository
) : DeregisterTokenUseCase {


    override suspend operator fun invoke(): DeregisterTokenUseCase.Result =
        notificationTokenRepository.getNotificationToken().flatMap { notiToken ->
            clientRepository.deregisterToken(notiToken.token)
        }.fold({
            if (it is NetworkFailure.ServerMiscommunication &&
                it.kaliumException is KaliumException.InvalidRequestError &&
                it.kaliumException.isNotFound()
            ) {
                DeregisterTokenUseCase.Result.Failure.NotFound
            }
            DeregisterTokenUseCase.Result.Failure.Generic(it)
        }, {
            DeregisterTokenUseCase.Result.Success
        })
}
