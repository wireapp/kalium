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
package com.wire.kalium.persistence.cache

import app.cash.turbine.test
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FlowCacheTest {

    @Test
    fun givenAnElementIsCached_whenFetching2ndTime_thenShouldReturnWithoutFlowEmittingAgain() = runTest {
        val cache = FlowCache<Int, String>(backgroundScope)
        val itemKey = 42
        var providerExecCount = 0
        var emissionCount = 0
        val expectedItem = "First ever item!!!!"

        val result = cache.get(itemKey) {
            providerExecCount++
            flow {
                emissionCount++
                emit(expectedItem)
                delay(10.seconds)
            }
        }

        result.test {
            assertEquals(expectedItem, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val newResult = cache.get(itemKey) {
            throw IllegalStateException("This should NOT be called")
        }

        newResult.test {
            assertEquals(expectedItem, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, providerExecCount)
        assertEquals(1, emissionCount)
    }

    @Test
    fun givenCachedFlow_whenNoMoreCollectors_thenShouldClearTheCacheAfterTimeout() = runTest {
        val timeout = 3.seconds
        val cache = FlowCache<Int, String>(backgroundScope, timeout)
        val itemKey = 42
        val channel = Channel<String>(Channel.UNLIMITED)
        channel.send("First ever item!!!!")

        cache.get(itemKey) {
            channel.receiveAsFlow()
        }.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        advanceTimeBy(timeout + 1.seconds)
        var isNewFlowCreated = false
        val secondItem = "Second ever item!!!!"
        channel.send(secondItem)
        cache.get(itemKey) {
            isNewFlowCreated = true
            channel.receiveAsFlow()
        }.test {
            assertEquals(secondItem, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(isNewFlowCreated)
    }

    @Test
    fun givenCachedFlow_whenNoMoreCollectors_thenCachedFlowShouldBeCancelled() = runTest {
        val timeout = 3.seconds
        val cache = FlowCache<Int, String>(backgroundScope, timeout)
        val itemKey = 42
        val channel = Channel<String>(Channel.UNLIMITED)
        channel.send("First ever item!!!!")
        channel.send("Another item!!!!")

        cache.get(itemKey) {
            channel.consumeAsFlow()
        }.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        advanceTimeBy(timeout + 1.seconds)

        assertTrue(channel.isClosedForSend)
    }
}
