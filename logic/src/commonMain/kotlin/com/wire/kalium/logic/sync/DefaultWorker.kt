package com.wire.kalium.logic.sync


sealed class Result {
    object Success : Result()
    object Failure : Result()
    object Retry : Result()
}

abstract class DefaultWorker {

    abstract suspend fun doWork(): Result
}
