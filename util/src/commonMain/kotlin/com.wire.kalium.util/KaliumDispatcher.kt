package com.wire.kalium.util

import kotlinx.coroutines.CoroutineDispatcher


interface KaliumDispatcher {
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
    val io: CoroutineDispatcher
}


expect object KaliumDispatcherImpl : KaliumDispatcher
