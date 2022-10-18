package com.wire.kalium.logic.test_util

import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

object TestKaliumDispatcher : KaliumDispatcher {

    private val testDispatcher = StandardTestDispatcher()

    override val default: TestDispatcher
        get() = testDispatcher

    override val main: TestDispatcher
        get() = testDispatcher

    override val unconfined: TestDispatcher
        get() = testDispatcher

    override val io: TestDispatcher
        get() = testDispatcher

}
