/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature

import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UserSessionRegistryConcurrencyTest {
    private val user = UserId("user", "example.com")

    @Test
    fun concurrentFirstRequestsConstructAndStartOneOwner() {
        val account = UserSessionRegistry<Any>({}, {}).account(user)
        val workers = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(1)
        val finishStartup = CountDownLatch(1)
        val creates = AtomicInteger()
        val starts = AtomicInteger()
        try {
            val barrier = CyclicBarrier(8)
            val requests = (1..8).map {
                workers.submit(Callable {
                    barrier.await(10, TimeUnit.SECONDS)
                    account.getOrCreate(create = { creates.incrementAndGet(); Any() }, start = { owner ->
                        starts.incrementAndGet()
                        assertSame(owner, account.getRaw())
                        assertNull(account.get())
                        ready.countDown()
                        check(finishStartup.await(10, TimeUnit.SECONDS))
                    })
                })
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            assertNull(account.get())
            assertTrue(requests.none { it.isDone })
            finishStartup.countDown()
            val owner = requests.first().get(10, TimeUnit.SECONDS)
            requests.forEach { assertSame(owner, it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, creates.get())
            assertEquals(1, starts.get())
            assertSame(owner, account.get())
        } finally {
            finishStartup.countDown()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun invalidationDuringConstructionOrStartupNeverPublishesAndCleanupWaitsForUnwind() = runTest {
        for (invalidateDuringStartup in listOf(false, true)) {
            val invalidated = AtomicInteger()
            val registry = UserSessionRegistry<Any>(invalidate = { invalidated.incrementAndGet() }, cancelFailedStartup = {})
            val account = registry.account(user)
            val workers = Executors.newSingleThreadExecutor()
            val entered = CountDownLatch(1)
            val finish = CountDownLatch(1)
            val starts = AtomicInteger()
            val owner = Any()
            try {
                val creation = workers.submit(Callable {
                    runCatching {
                        account.getOrCreate(create = {
                            if (!invalidateDuringStartup) {
                                entered.countDown()
                                check(finish.await(10, TimeUnit.SECONDS))
                            }
                            owner
                        }, start = {
                            starts.incrementAndGet()
                            if (invalidateDuringStartup) {
                                entered.countDown()
                                check(finish.await(10, TimeUnit.SECONDS))
                            }
                        })
                    }
                })
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                val cleanup = async(start = CoroutineStart.UNDISPATCHED) {
                    account.withLifecycle {
                        account.close().also { account.release() }
                    }
                }
                assertFalse(cleanup.isCompleted)
                assertNull(account.get())
                val other = registry.account(UserId(user.value, "other.example"))
                val otherOwner = Any()
                assertSame(otherOwner, other.getOrCreate(create = { otherOwner }, start = {}))
                finish.countDown()
                assertIs<IllegalStateException>(creation.get(10, TimeUnit.SECONDS).exceptionOrNull())
                assertSame(owner, cleanup.await())
                assertEquals(if (invalidateDuringStartup) 1 else 0, starts.get())
                assertTrue(invalidated.get() > 0)
                assertNull(account.get())
                val next = Any()
                assertSame(next, account.getOrCreate(create = { next }, start = {}))
            } finally {
                finish.countDown()
                workers.shutdownNow()
                assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }
}
