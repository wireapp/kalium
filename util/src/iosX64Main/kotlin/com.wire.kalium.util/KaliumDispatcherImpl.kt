package com.wire.kalium.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext

actual object KaliumDispatcherImpl : KaliumDispatcher {
    override val default: CoroutineDispatcher
        get() = Dispatchers.Default
    override val main: MainCoroutineDispatcher
        get() = Dispatchers.Main
    override val unconfined: CoroutineDispatcher
        get() = Dispatchers.Unconfined
    override val io: CoroutineDispatcher
        get() = Dispatchers.Default
    override val database: CoroutineDispatcher
        get() = newSingleThreadContext("SingleThreadDispatcher")
}
