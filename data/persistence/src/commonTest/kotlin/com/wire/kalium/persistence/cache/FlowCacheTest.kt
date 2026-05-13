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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    @Test
    fun givenEntryRecreatedAfterEviction_whenOldUpstreamLatelyCompletes_thenNewEntryIsNotRemoved() = runTest {
        val timeout = 3.seconds
        val cache = FlowCache<Int, String>(backgroundScope, timeout)
        val itemKey = 42

        val firstChannel = Channel<String>(Channel.UNLIMITED)
        firstChannel.send("first")
        cache.get(itemKey) { firstChannel.receiveAsFlow() }.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Explicitly evict — this cancels the old sharingJob; the old upstream's
        // onCompletion will fire asynchronously as cancellation propagates.
        cache.remove(itemKey)

        // Recreate the entry under the same key before/while the cancellation propagates.
        val secondChannel = Channel<String>(Channel.UNLIMITED)
        secondChannel.send("second")
        cache.get(itemKey) { secondChannel.receiveAsFlow() }.test {
            assertEquals("second", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Give the late onCompletion of the OLD upstream a chance to fire.
        advanceTimeBy(1.seconds)

        // If the race regressed, the late onCompletion of the OLD flow would have wiped
        // sharingJobs[itemKey], so the next get() would invoke a fresh producer instead
        // of reusing the cached "second" replay value.
        var thirdProducerCalled = false
        cache.get(itemKey) {
            thirdProducerCalled = true
            Channel<String>(Channel.UNLIMITED).receiveAsFlow()
        }.test {
            assertEquals("second", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(
            thirdProducerCalled,
            "Late onCompletion of evicted flow incorrectly removed the newly-recreated entry"
        )
    }

    @Test
    fun givenMultipleCacheEvictions_thenSharingCoroutinesShouldNotAccumulate() = runTest {
        val timeout = 3.seconds
        val cache = FlowCache<Int, String>(backgroundScope, timeout)
        val itemKey = 42

        repeat(5) {
            val channel = Channel<String>(Channel.UNLIMITED)
            channel.send("item")
            cache.get(itemKey) { channel.receiveAsFlow() }.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(timeout + 1.seconds)
        }

        val children = backgroundScope.coroutineContext[Job]!!.children.toList()
        children.forEach { child ->
            assertTrue(
                child.isCompleted || child.isCancelled,
                "Sharing job $child should be completed/cancelled after eviction, but was still active"
            )
        }
        assertEquals(
            0,
            children.size,
            "Expected 0 active coroutines but found ${children.size} zombie(s): $children"
        )
    }

    @Test
    fun givenFlowProducerThrows_whenGetIsCalled_thenNoOrphanSharingJobIsLeftBehind() = runTest {
        val cache = FlowCache<Int, String>(backgroundScope)
        val itemKey = 42
        val parentJob = backgroundScope.coroutineContext[Job]!!
        val childrenBefore = parentJob.children.count()

        val error = assertFailsWith<RuntimeException> {
            cache.get(itemKey) { throw RuntimeException("producer failed") }
        }
        assertEquals("producer failed", error.message)

        // A failed producer must not pollute the cache with an orphan SupervisorJob.
        val childrenAfter = parentJob.children.count()
        assertEquals(
            childrenBefore,
            childrenAfter,
            "Expected no new children after failed get, but ${childrenAfter - childrenBefore} orphan job(s) remain"
        )

        // The cache must still work normally after a failed producer call.
        val channel = Channel<String>(Channel.UNLIMITED)
        channel.send("ok")
        cache.get(itemKey) { channel.receiveAsFlow() }.test {
            assertEquals("ok", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSubscribedFlow_whenCacheScopeJobIsCancelled_thenSharingJobIsAlsoCancelled() = runTest {
        // Verifies the SupervisorJob parent-child wiring: cancelling cacheScope's Job must
        // propagate to all sharing jobs. Regression guard against changing
        // SupervisorJob(cacheScope.coroutineContext[Job]) to SupervisorJob() (orphan).
        val cacheJob = Job(parent = backgroundScope.coroutineContext[Job])
        val cacheScope = CoroutineScope(backgroundScope.coroutineContext + cacheJob)
        val cache = FlowCache<Int, String>(cacheScope)
        val channel = Channel<String>(Channel.UNLIMITED)
        channel.send("test")

        cache.get(42) { channel.receiveAsFlow() }.test {
            assertEquals("test", awaitItem())
            // While subscribed, sharingJob should be a registered child of cacheJob.
            assertTrue(
                cacheJob.children.any { it.isActive },
                "Expected cacheJob to have an active child sharing job while subscribed"
            )
            cancelAndIgnoreRemainingEvents()
        }

        cacheJob.cancel()

        cacheJob.children.forEach { child ->
            assertTrue(
                child.isCancelled || child.isCompleted,
                "Sharing job $child not cancelled when cacheScope was cancelled"
            )
        }
    }

    @Test
    fun givenCacheScopeWithoutJob_whenFlowCacheIsConstructed_thenItRejectsTheScope() = runTest {
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }
        assertFailsWith<IllegalArgumentException> {
            FlowCache<Int, String>(noJobScope)
        }
    }
}
