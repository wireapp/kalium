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

    private fun registry() = UserSessionRegistry<Owner>(
        invalidate = { it.invalidated = true },
        cancelFailedStartup = { it.cancelled = true },
    )

    private class Owner {
        var invalidated = false
        var cancelled = false
    }
}
