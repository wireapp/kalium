package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold

class DeregisterTokenUseCase(
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository
) {
    sealed class Result {
        object Success : Result()
        sealed class Failure : Result() {
            data class Generic(val coreFailure: CoreFailure) : Failure()
        }
    }

    suspend operator fun invoke(): Result = notificationTokenRepository.getNotificationToken().flatMap { notiToken ->
        clientRepository.deregisterToken(notiToken.token)
    }.fold({
        Result.Failure.Generic(it)
    }, {
        Result.Success
    })
}
