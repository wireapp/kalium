package com.wire.kalium.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher

expect object KaliumDispatcher {
     val default: CoroutineDispatcher
     val main: MainCoroutineDispatcher
     val unconfined: CoroutineDispatcher
     val io: CoroutineDispatcher
}
