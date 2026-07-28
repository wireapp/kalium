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

package com.wire.kalium.notificationextension

import com.wire.kalium.synccoordination.ProcessLockLease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationExtensionLeaseTeardownTest {
    @Test
    fun givenTeardownFails_whenReleasing_thenNativeLeaseIsRetainedAndRepeatedReleaseIsIdempotent() {
        var nativeReleaseCalls = 0
        val teardownState = NotificationExtensionTeardownState()
        val lease = CloseResourcesThenProcessLease(
            closeAttemptResources = { error("unsafe-teardown") },
            nativeLease = ProcessLockLease { nativeReleaseCalls += 1 },
            teardownState = teardownState
        )

        lease.release()
        lease.release()

        assertEquals(0, nativeReleaseCalls)
        assertTrue(teardownState.isUnsafe)
    }

    @Test
    fun givenTeardownSucceeds_whenReleasingRepeatedly_thenNativeLeaseIsReleasedExactlyOnce() {
        var nativeReleaseCalls = 0
        val teardownState = NotificationExtensionTeardownState()
        val lease = CloseResourcesThenProcessLease(
            closeAttemptResources = {},
            nativeLease = ProcessLockLease { nativeReleaseCalls += 1 },
            teardownState = teardownState
        )

        lease.release()
        lease.release()

        assertEquals(1, nativeReleaseCalls)
        assertFalse(teardownState.isUnsafe)
    }
}
