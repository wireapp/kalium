/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DebounceBufferTest {

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatcher.default)
    }

    @Test
    fun givenNewItem_whenObserving_thenEmitListAfterTimeout() = runTest {
        val (_, debounceBuffer) = Arrangement().arrange()
        advanceUntilIdle()

        debounceBuffer.observe().test {
            debounceBuffer.add("1")
            expectNoEvents()

            advanceTimeBy(3.seconds)
            assertEquals(listOf("1"), expectMostRecentItem())

            cancel()
        }
    }

    @Test
    fun givenNewItemsKeepIncoming_whenObserving_thenEmitListAfterMaxSize() = runTest {
        val (_, debounceBuffer) = Arrangement().arrange()
        advanceUntilIdle()

        debounceBuffer.observe().test {
            expectNoEvents()

            for (i in 1..5) {
                debounceBuffer.add(i.toString())
                if (i < 5) expectNoEvents()
                else assertEquals((1..5).map { it.toString() }, awaitItem())
                delay(1.seconds)
            }

            cancel()
        }
    }

    @Test
    fun givenNewItemsKeepIncomingAndStop_whenObserving_thenEmitListAfterMaxSizeAndTheRestAfterTimeout() = runTest {
        val (_, debounceBuffer) = Arrangement().arrange()
        advanceUntilIdle()

        debounceBuffer.observe().test {
            expectNoEvents()

            for (i in 1..7) {
                delay(1.seconds)
                debounceBuffer.add(i.toString())
                if (i != 5) expectNoEvents()
                else assertEquals((1..5).map { it.toString() }, awaitItem())
            }

            advanceTimeBy(3.seconds)
            assertEquals((6..7).map { it.toString() }, awaitItem())

            cancel()
        }
    }

    internal class Arrangement {
        private val debounceBuffer = DebounceBuffer<String>(capacity = 5, timeout = 2.seconds, scope = CoroutineScope(dispatcher.default))
        fun arrange() = this to debounceBuffer
    }

    companion object {
        private val dispatcher = TestKaliumDispatcher
    }
}
