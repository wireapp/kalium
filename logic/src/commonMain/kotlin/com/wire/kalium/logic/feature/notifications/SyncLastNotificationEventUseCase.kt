package com.wire.kalium.logic.feature.notifications

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.LastNotificationEventRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.wrapApiRequest

//class SyncLastNotificationEventUseCase(
//    private val apiClient: Client,
//    private val lastRetrievedNotificationRepository: LastNotificationEventRepository
//) {
//
//    operator fun invoke(): Result =
//        wrapApiRequest { apiClient }
//        lastRetrievedNotificationRepository
//            .getLastNotificationEventId()
//            .fold({
//                Result.Failure.Generic(it)
//            }, {
//                Result.Success
//            })
//
//    sealed class Result {
//        object Success : Result()
//        sealed class Failure : Result() {
//            class Generic(val failure: StorageFailure) : Failure()
//        }
//    }
//}
