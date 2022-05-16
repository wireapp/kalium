package com.wire.kalium.logic.sync


sealed class Result {
    object Success : Result()
    object Failure : Result()
    object Retry : Result()
}

interface DefaultWorker {
    suspend fun doWork(): Result
}
