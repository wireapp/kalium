package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.UserSessionScope

sealed class Result {
    object Success : Result()
    object Failure : Result()
    object Retry : Result()
}

// TODO: 03/05/2022 Remove mandatory UserSessionScope to enable testing of workers
//  We can have specialised factories to create workers if needed, passing only
//  dependencies instead of a full UserSessionScope, see how Android schedules PendingMessagesSenderWorker
abstract class UserSessionWorker(val userSessionScope: UserSessionScope) {

    abstract suspend fun doWork(): Result

}
