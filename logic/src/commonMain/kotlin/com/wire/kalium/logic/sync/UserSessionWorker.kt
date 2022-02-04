package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.UserSessionScope

sealed class Result {
    object Success : Result()
    object Failure : Result()
    object Retry : Result()
}

abstract class UserSessionWorker(val userSessionScope: UserSessionScope) {

    abstract suspend fun doWork(): Result

}
