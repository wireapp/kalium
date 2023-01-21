package com.wire.kalium.util

import kotlinx.coroutines.CoroutineDispatcher

interface KaliumDispatcher {
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
    val io: CoroutineDispatcher
    val database: CoroutineDispatcher
}

expect object KaliumDispatcherImpl : KaliumDispatcher
