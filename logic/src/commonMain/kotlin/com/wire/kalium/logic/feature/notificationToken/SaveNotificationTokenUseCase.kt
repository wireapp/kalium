package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.functional.fold


class SaveNotificationTokenUseCase(
    private val notificationTokenRepository: NotificationTokenRepository
) {

    operator fun invoke(token: String, type: String): Result =
        notificationTokenRepository.persistNotificationToken(token, type).fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success
        })


    sealed class Result {
        object Success : Result()
        sealed class Failure : Result() {
            class Generic(val failure: StorageFailure) : Failure()
        }
    }
}
