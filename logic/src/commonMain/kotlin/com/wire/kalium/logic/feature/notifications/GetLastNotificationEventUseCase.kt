package com.wire.kalium.logic.feature.notifications

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.LastNotificationEventRepository
import com.wire.kalium.logic.functional.fold

class GetLastNotificationEventUseCase(
    private val lastRetrievedNotificationRepository: LastNotificationEventRepository
    ) {

    operator fun invoke(): Result =
        lastRetrievedNotificationRepository
            .getLastNotificationEventId()
            .fold({
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
