package com.wire.kalium.logic.test_util

import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

object TestKaliumDispatcher : KaliumDispatcher {

    private val testDispatcher = StandardTestDispatcher()

    override val default: CoroutineDispatcher
        get() = testDispatcher

    override val main: CoroutineDispatcher
        get() = testDispatcher

    override val unconfined: CoroutineDispatcher
        get() = testDispatcher

    override val io: CoroutineDispatcher
        get() = testDispatcher

}
