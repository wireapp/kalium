package com.wire.kalium.logic.feature.notifications

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.LastNotificationEventRepository
import com.wire.kalium.logic.functional.fold

class SaveLastNotificationEventUseCase(
    private val lastRetrievedNotificationRepository: LastNotificationEventRepository
    ) {

    operator fun invoke(eventId: String): Result =
        lastRetrievedNotificationRepository
            .persistLastNotificationEventId(id = eventId)
            .fold({
                Result.Failure.Generic(it)
            }, {
                Result.Success
            })

    sealed class Result {
        object Success : SaveLastNotificationEventUseCase.Result()
        sealed class Failure : SaveLastNotificationEventUseCase.Result() {
            class Generic(val failure: StorageFailure) : Failure()
        }
    }
}
