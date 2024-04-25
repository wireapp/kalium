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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ExpirableCacheTest {

    private val dispatcher = StandardTestDispatcher()
    private val currentTime: CurrentTimeProvider = { Instant.fromEpochMilliseconds(dispatcher.scheduler.currentTime) }

    @Test
    fun `before the cache expiration the cached value is still available`() = runTest(dispatcher) {
        val cache = ExpirableCache<String, String>(5.seconds, currentTime)
        var createCallCount = 0

        val value1 = cache.getOrPut("key") { "value1".also { createCallCount++ } }
        advanceTimeBy(1.seconds)
        val value2 = cache.getOrPut("key") { "value2".also { createCallCount++ } }

        assertEquals(value1, value2)
        assertEquals(1, createCallCount)
    }

    @Test
    fun `after the cache expiration the cached value is no more available`() = runTest(dispatcher) {
        val cache = ExpirableCache<String, String>(5.seconds, currentTime)
        var createCallCount = 0

        val value1 = cache.getOrPut("key") { "value1".also { createCallCount++ } }
        advanceTimeBy(6.seconds)
        val value2 = cache.getOrPut("key") { "value2".also { createCallCount++ } }

        assertNotEquals(value1, value2)
        assertEquals(2, createCallCount)
    }

}
