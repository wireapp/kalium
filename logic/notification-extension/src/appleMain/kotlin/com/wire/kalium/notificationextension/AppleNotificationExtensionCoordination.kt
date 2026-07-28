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
import kotlin.concurrent.atomics.AtomicReference

/** M5-to-M4 Apple adapter with cancellation-safe native ownership transfer. */
internal class AppleNotificationSyncLeaseCoordinator(
    sharedAppGroupRoot: String,
    private val closeAttemptResources: () -> Unit,
    private val teardownState: NotificationExtensionTeardownState = NotificationExtensionTeardownState()
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
            CloseResourcesThenProcessLease(closeAttemptResources, nativeLease, teardownState)
        )
    }
}

/** Resource teardown is part of lease release, so M6/CoreCrypto/AVS close before `flock` unlock. */
internal class CloseResourcesThenProcessLease(
    private val closeAttemptResources: () -> Unit,
    private val nativeLease: ProcessLockLease,
    private val teardownState: NotificationExtensionTeardownState
) : NotificationSyncLease {
    private val released = AtomicInt(LEASE_OWNED)

    override fun release() {
        if (!released.compareAndSet(LEASE_OWNED, LEASE_RELEASING)) return
        if (runCatching(closeAttemptResources).isFailure) {
            teardownState.markUnsafe()
            retainUnsafeClientLeaseUntilProcessExit(nativeLease)
            released.store(LEASE_RETAINED_UNTIL_PROCESS_EXIT)
            return
        }
        nativeLease.release()
        released.store(LEASE_RELEASED)
    }
}

internal class NotificationExtensionTeardownState {
    private val state = AtomicInt(TEARDOWN_SAFE)

    val isUnsafe: Boolean
        get() = state.load() == TEARDOWN_UNSAFE

    fun markUnsafe() {
        state.store(TEARDOWN_UNSAFE)
    }
}

private fun retainUnsafeClientLeaseUntilProcessExit(lease: ProcessLockLease) {
    while (true) {
        val current = RETAINED_UNSAFE_CLIENT_LEASES.load()
        if (RETAINED_UNSAFE_CLIENT_LEASES.compareAndSet(current, current + lease)) return
    }
}

private const val LEASE_OWNED = 0
private const val LEASE_RELEASING = 1
private const val LEASE_RELEASED = 2
private const val LEASE_RETAINED_UNTIL_PROCESS_EXIT = 3
private const val TEARDOWN_SAFE = 0
private const val TEARDOWN_UNSAFE = 1
private val RETAINED_UNSAFE_CLIENT_LEASES = AtomicReference<List<ProcessLockLease>>(emptyList())
