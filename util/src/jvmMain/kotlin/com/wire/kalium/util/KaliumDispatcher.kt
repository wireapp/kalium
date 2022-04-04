package com.wire.kalium.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

actual object KaliumDispatcher {
    actual val default: CoroutineDispatcher
        get() = Dispatchers.Default
    actual val main: MainCoroutineDispatcher
        get() = Dispatchers.Main
    actual val unconfined: CoroutineDispatcher
        get() = Dispatchers.Unconfined
    actual val io: CoroutineDispatcher
        get() = Dispatchers.IO
}
