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

import com.wire.kalium.logic.di.ConsumerWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ObservationCancellationRecoveryTest {
    @Test
    fun actualRecoveredCancellationRetainsOriginalOwnerInCauseChain() {
        // Default suites explicitly skip this mode-specific proof. The parent must run it with
        // debug=on on the TEST JVM before coroutines initialize, then verify it was not skipped.
        assumeTrue("Requires JVM -Dkotlinx.coroutines.debug=on", System.getProperty("kotlinx.coroutines.debug") == "on")
        assertEquals("true", System.getProperty("kotlinx.coroutines.stacktrace.recovery", "true"))
        runTest {
            val revoked = CancellationException("owned observer revocation")
            val work = ConsumerWork(revocation = revoked)
            val entered = CompletableDeferred<Unit>()
            val operation = async {
                runCatching {
                    work.execute {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                }.exceptionOrNull()
            }
            // StandardTestDispatcher queues the parent continuation: execute is suspended before
            // this await resumes, so withContext returns through the actual recovery slow path.
            entered.await()
            work.close()
            val recovered = assertIs<CancellationException>(operation.await())
            assertNotSame(revoked, recovered, "Stack recovery was not exercised by this JVM")
            assertTrue(recovered.hasCauseIdentity(revoked))
            work.awaitClosed()
        }
    }

    @Test
    fun identityMatchingRejectsSameMessageAndSuppressedOwnerAndTerminatesCauseCycles() {
        val owned = CancellationException("same message")
        val unrelated = CancellationException("same message")
        unrelated.addSuppressed(owned)
        assertFalse(unrelated.hasCauseIdentity(owned))
        val copied = CancellationException("recovered wrapper").also { it.initCause(owned) }
        assertTrue(copied.hasCauseIdentity(owned))
        val first = CancellationException("cycle one")
        val second = CancellationException("cycle two")
        first.initCause(second)
        second.initCause(first)
        assertFalse(first.hasCauseIdentity(owned))
        assertTrue(first.hasCauseIdentity(second))
    }
}
