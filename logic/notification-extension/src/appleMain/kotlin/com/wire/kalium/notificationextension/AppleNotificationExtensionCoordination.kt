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

@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:Suppress("Filename", "MatchingDeclarationName")

package com.wire.kalium.notificationextension

import com.wire.kalium.notificationsync.LeaseAcquireResult
import com.wire.kalium.notificationsync.NotificationSyncLease
import com.wire.kalium.notificationsync.NotificationSyncLeaseCoordinator
import com.wire.kalium.notificationsync.NotificationSyncScope
import com.wire.kalium.synccoordination.AppleProcessLockFactory
import com.wire.kalium.synccoordination.ProcessLockAcquireResult
import com.wire.kalium.synccoordination.ProcessLockLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.concurrent.atomics.AtomicInt

/** M5-to-M4 Apple adapter with cancellation-safe native ownership transfer. */
internal class AppleNotificationSyncLeaseCoordinator(
    sharedAppGroupRoot: String,
    private val closeAttemptResources: () -> Unit
) : NotificationSyncLeaseCoordinator {
    private val factory = AppleProcessLockFactory(sharedAppGroupRoot)

    override suspend fun tryAcquire(scope: NotificationSyncScope): LeaseAcquireResult {
        currentCoroutineContext().ensureActive()
        return when (val result = factory.tryAcquire(scope.accountId, scope.clientId)) {
            is ProcessLockAcquireResult.Acquired -> transferAcquiredLease(result.lease)
            ProcessLockAcquireResult.Unavailable -> LeaseAcquireResult.Unavailable
            is ProcessLockAcquireResult.RetryableFailure -> LeaseAcquireResult.RetryableFailure
            is ProcessLockAcquireResult.TerminalFailure -> LeaseAcquireResult.TerminalFailure
        }
    }

    private suspend fun transferAcquiredLease(nativeLease: ProcessLockLease): LeaseAcquireResult {
        try {
            currentCoroutineContext().ensureActive()
        } catch (cancellation: CancellationException) {
            nativeLease.release()
            throw cancellation
        }
        return LeaseAcquireResult.Acquired(
            CloseResourcesThenProcessLease(closeAttemptResources, nativeLease)
        )
    }
}

/** Resource teardown is part of lease release, so M6/CoreCrypto/AVS close before `flock` unlock. */
private class CloseResourcesThenProcessLease(
    private val closeAttemptResources: () -> Unit,
    private val nativeLease: ProcessLockLease
) : NotificationSyncLease {
    private val released = AtomicInt(LEASE_OWNED)

    override fun release() {
        if (!released.compareAndSet(LEASE_OWNED, LEASE_RELEASED)) return
        try {
            runCatching(closeAttemptResources)
        } finally {
            nativeLease.release()
        }
    }
}

private const val LEASE_OWNED = 0
private const val LEASE_RELEASED = 1
