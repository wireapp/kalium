/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.util

import app.cash.turbine.test
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TriggerBufferTest {

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatcher.default)
    }

    @Test
    fun givenNewItemsAndTriggerIsFalse_whenObserving_thenDoNotEmit() = runTest {
        val (_, triggerBuffer) = Arrangement(false).arrange()
        advanceUntilIdle()

        triggerBuffer.observe().test {
            triggerBuffer.add("1")
            triggerBuffer.add("2")
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun givenNewItemsAndTriggerIsTrue_whenObserving_thenEmitRightAway() = runTest {
        val (_, triggerBuffer) = Arrangement(true).arrange()
        advanceUntilIdle()

        triggerBuffer.observe().test {
            triggerBuffer.add("1")
            assertEquals(listOf("1"), awaitItem())

            triggerBuffer.add("2")
            assertEquals(listOf("2"), awaitItem())

            cancel()
        }
    }

    @Test
    fun givenNewItemsAndTriggerIsFalse_whenObservingAndTriggerChanges_thenEmitAfterTriggerChange() = runTest {
        val (arrangement, triggerBuffer) = Arrangement(false).arrange()
        advanceUntilIdle()

        triggerBuffer.observe().test {
            triggerBuffer.add("1")
            triggerBuffer.add("2")
            expectNoEvents()

            arrangement.trigger.emit(true)
            assertEquals(listOf("1", "2"), awaitItem())

            cancel()
        }
    }

    internal class Arrangement(initialTriggerValue: Boolean) {
        val trigger = MutableStateFlow(initialTriggerValue)
        private val triggerBuffer = TriggerBuffer<String>(trigger, scope = CoroutineScope(dispatcher.default))
        fun arrange() = this to triggerBuffer
    }

    companion object {
        private val dispatcher = TestKaliumDispatcher
    }
}
