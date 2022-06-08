package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold

interface DeregisterTokenUseCase {
    suspend operator fun invoke()
}
class DeregisterTokenUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository
): DeregisterTokenUseCase {
    sealed class Result {
        object Success : Result()
        sealed class Failure : Result() {
            object NotFound : Failure()
            data class Generic(val coreFailure: CoreFailure) : Failure()
        }
    }

    override suspend operator fun invoke(): Result = notificationTokenRepository.getNotificationToken().flatMap { notiToken ->
        clientRepository.deregisterToken(notiToken.token)
    }.fold({
        TODO("check the error lable for token not found")
        Result.Failure.Generic(it)
    }, {
        Result.Success
    })
}
