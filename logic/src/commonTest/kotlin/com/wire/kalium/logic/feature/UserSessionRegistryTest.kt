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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UserSessionRegistryTest {
    private val user = UserId("user", "example.com")

    @Test
    fun privateOwnerIsRetainedBeforeStartupAndPublishedOnlyAfterItReturns() {
        val account = registry().account(user)
        val owner = Owner()
        var starts = 0
        assertSame(owner, account.getOrCreate(create = { owner }, start = {
            starts++
            assertSame(owner, account.getRaw())
            assertTrue(account.isCurrent(owner))
            assertNull(account.get())
            assertFailsWith<IllegalStateException> {
                account.getOrCreate(create = { error("Reentrant construction") }, start = {})
            }
        }))
        assertSame(owner, account.get())
        assertSame(owner, account.getOrCreate(create = { error("Duplicate construction") }, start = { starts++ }))
        assertEquals(1, starts)
    }

    @Test
    fun startupFailureQuarantinesTheActualOwnerAndCancelsItsWork() = runTest {
        for (failure in listOf(IllegalStateException("startup"), CancellationException("startup cancelled"))) {
            val account = registry().account(user)
            val owner = Owner()
            assertSame(failure, runCatching {
                account.getOrCreate(create = { owner }, start = { throw failure })
            }.exceptionOrNull())
            assertTrue(owner.invalidated)
            assertTrue(owner.cancelled)
            assertTrue(account.hasFailedStartup())
            assertNull(account.get())
            assertSame(owner, account.getRaw())
            assertFailsWith<IllegalStateException> { account.getOrCreate(create = { Owner() }, start = {}) }
            account.withLifecycle {
                assertSame(owner, account.close())
                account.release()
            }
            val next = Owner()
            assertSame(next, account.getOrCreate(create = { next }, start = {}))
            assertFalse(account.isCurrent(owner))
        }
    }

    @Test
    fun constructorFailureClosesAdmissionUntilExplicitCleanup() = runTest {
        val account = registry().account(user)
        assertFailsWith<IllegalArgumentException> {
            account.getOrCreate(create = { throw IllegalArgumentException("partial construction") }, start = {})
        }
        assertTrue(account.hasFailedStartup())
        assertNull(account.getRaw())
        assertFailsWith<IllegalStateException> { account.getOrCreate(create = { Owner() }, start = {}) }
        account.withLifecycle {
            assertNull(account.close())
            account.release()
        }
        val next = Owner()
        assertSame(next, account.getOrCreate(create = { next }, start = {}))
    }

    @Test
    fun failedOrCancelledCleanupRetainsOwnerUntilRetrySucceeds() = runTest {
        val account = registry().account(user)
        val owner = account.getOrCreate(create = { Owner() }, start = {})
        for (failure in listOf(IllegalStateException("provider removal"), CancellationException("cleanup cancelled"))) {
            assertSame(failure, runCatching {
                account.withLifecycle {
                    assertSame(owner, account.close())
                    throw failure
                }
            }.exceptionOrNull())
            assertNull(account.get())
            assertSame(owner, account.getRaw())
            assertFailsWith<IllegalStateException> { account.getOrCreate(create = { Owner() }, start = {}) }
        }
        account.withLifecycle {
            assertSame(owner, account.close())
            account.release()
        }
        val next = Owner()
        assertSame(next, account.getOrCreate(create = { next }, start = {}))
    }

    @Test
    fun qualifiedAccountsKeepIndependentGenerationsAndUnusedInvalidationCreatesNothing() = runTest {
        val registry = registry()
        val first = registry.account(user)
        val other = registry.account(UserId(user.value, "other.example"))
        val owner = other.getOrCreate(create = { Owner() }, start = {})
        first.invalidate()
        assertNull(first.getRaw())
        assertSame(owner, other.get())
        assertFalse(owner.invalidated)
        first.withLifecycle {
            assertNull(first.close())
            first.release()
        }
        assertFalse(owner.cancelled)
    }

    @Test
    fun observerDrainIsOutsideLifecycleAndPrecedesCleanupEvenForNonCancellableUnwind() = runTest {
        val account = registry().account(user)
        val owner = account.getOrCreate(create = { Owner() }, start = {})
        val admitted = checkNotNull(account.admit(account.revision.value, owner))
        val entered = CompletableDeferred<Unit>()
        val unwinding = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var finishedUnwind = false
        val operation = launch {
            try {
                admitted.work.execute {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) {
                            unwinding.complete(Unit)
                            finish.await()
                            finishedUnwind = true
                        }
                    }
                }
            } finally {
                admitted.work.close()
            }
        }
        entered.await()
        var removed = false
        val closing = async {
            account.withClosed { outgoing ->
                assertSame(owner, outgoing)
                assertTrue(finishedUnwind)
                removed = true
                account.release()
            }
        }
        unwinding.await()
        assertFalse(admitted.isCurrent())
        assertFalse(removed)
        // If closure held lifecycle while awaiting observer unwind, this assertion would deadlock.
        account.withLifecycle { assertFalse(removed) }
        finish.complete(Unit)
        closing.await()
        operation.join()
        assertTrue(removed)
    }

    @Test
    fun staleRevisionNeverAdmitsAndQualifiedUsersKeepIndependentObserverOwnership() = runTest {
        val registry = registry()
        val account = registry.account(user)
        val other = registry.account(UserId(user.value, "other.example"))
        val first = account.getOrCreate(create = { Owner() }, start = {})
        val otherOwner = other.getOrCreate(create = { Owner() }, start = {})
        val revision = account.revision.value
        val old = checkNotNull(account.admit(revision, first))
        val independent = checkNotNull(other.admit(other.revision.value, otherOwner))
        account.withClosed { account.release() }
        assertFalse(old.isCurrent())
        assertTrue(independent.isCurrent())
        assertTrue(account.revision.value > revision)
        val second = account.getOrCreate(create = { Owner() }, start = {})
        account.allowObservation(second) // The provider authorizes observation after manual construction returns.
        assertNull(account.admit(revision, second))
        val current = checkNotNull(account.admit(account.revision.value, second))
        assertTrue(current.isCurrent())
        // A stale logout must not invalidate or mutate the newly published owner.
        assertNull(account.withClosed<Unit>(first) { error("Stale logout entered cleanup") })
        assertTrue(current.isCurrent())
        current.work.close()
        independent.work.close()
    }

    @Test
    fun releaseDisablesOnlyObservationAndManualEnableCannotPublishStartingOwner() = runTest {
        val account = registry().account(user)
        val beforeRelease = account.revision.value
        account.withClosed { account.release() }
        val released = account.revision.value
        assertFalse(account.isAvailable(released))
        assertFailsWith<SessionGenerationClosed> {
            account.getOrCreate(create = { error("Stale observer constructed after release") }, start = {},
                observationRevision = beforeRelease)
        }
        assertFailsWith<SessionGenerationClosed> {
            account.getOrCreate(create = { error("Idle observer converted itself to a manual open") }, start = {},
                observationRevision = released)
        }
        val next = account.getOrCreate(create = { Owner() }, start = { owner ->
            account.allowObservation(owner)
            assertEquals(released, account.revision.value, "Starting owner woke collectLatest")
            assertFalse(account.isAvailable(released))
            assertNull(account.get())
        })
        assertSame(next, account.get()) // Compatibility access is allowed while automatic observation is idle.
        assertEquals(released, account.revision.value)
        account.allowObservation(next) // Called by provider only after getOrCreate/startup has fully returned.
        assertTrue(account.isAvailable(account.revision.value))
        val ready = account.revision.value
        account.allowObservation(next)
        assertEquals(ready, account.revision.value, "An already-enabled observer would cancel its own startup")
        account.withClosed { account.release(allowObservation = true) }
        assertTrue(account.isAvailable(account.revision.value), "Successful credential store should resume observers")
    }

    private fun registry() = UserSessionRegistry<Owner>(
        invalidate = { it.invalidated = true },
        cancelFailedStartup = { it.cancelled = true },
    )

    private class Owner {
        var invalidated = false
        var cancelled = false
    }
}
