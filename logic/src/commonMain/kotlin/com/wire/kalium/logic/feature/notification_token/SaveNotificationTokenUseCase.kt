package com.wire.kalium.logic.feature.notification_token

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository


class SaveNotificationTokenUseCase(
    private val notificationTokenRepository: NotificationTokenRepository
) {

    operator fun invoke(token: String, type: String) {
        notificationTokenRepository.persistNotificationToken(token, type).fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success
        })
    }

    sealed class Result {
        object Success : Result()
        sealed class Failure : Result() {
            class Generic(val failure: StorageFailure) : Failure()
        }
    }
}
