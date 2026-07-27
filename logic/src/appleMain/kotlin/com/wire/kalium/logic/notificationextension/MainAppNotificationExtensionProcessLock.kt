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

package com.wire.kalium.logic.notificationextension

import com.wire.kalium.synccoordination.AppleProcessLockFactory
import com.wire.kalium.synccoordination.ProcessLockAcquireResult
import com.wire.kalium.synccoordination.ProcessLockLease
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Swift-facing owner for the account-wide lock shared by foreground Kalium and the iOS NSE.
 *
 * The lock uses a fixed client identity because it must be acquired before either process opens
 * account storage to resolve the registered client. The narrower per-client NSE lock remains in
 * place inside the bounded synchronization engine.
 */
public class MainAppNotificationExtensionProcessLock(
    sharedAppGroupRoot: String
) {
    private val factory: AppleProcessLockFactory = AppleProcessLockFactory(sharedAppGroupRoot)

    public fun tryAcquire(
        userId: String
    ): MainAppNotificationExtensionProcessLockResult {
        if (userId.isBlank()) {
            return MainAppNotificationExtensionProcessLockResult(
                MainAppNotificationExtensionProcessLockStatus.TERMINAL_FAILURE,
                null
            )
        }
        return factory.tryAcquire(
            userId.trim().lowercase(),
            ACCOUNT_WIDE_PROCESS_LOCK_CLIENT_ID
        ).toMainAppResult()
    }
}

public enum class MainAppNotificationExtensionProcessLockStatus {
    ACQUIRED,
    UNAVAILABLE,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
}

@OptIn(ExperimentalAtomicApi::class)
public class MainAppNotificationExtensionProcessLockResult internal constructor(
    public val status: MainAppNotificationExtensionProcessLockStatus,
    lease: ProcessLockLease?
) {
    private val ownedLease: AtomicReference<ProcessLockLease?> = AtomicReference(lease)

    /** Bounded, idempotent release suitable for Swift lifecycle cleanup. */
    public fun release() {
        ownedLease.exchange(null)?.release()
    }
}

private fun ProcessLockAcquireResult.toMainAppResult(): MainAppNotificationExtensionProcessLockResult =
    when (this) {
        is ProcessLockAcquireResult.Acquired -> MainAppNotificationExtensionProcessLockResult(
            MainAppNotificationExtensionProcessLockStatus.ACQUIRED,
            lease
        )

        ProcessLockAcquireResult.Unavailable -> MainAppNotificationExtensionProcessLockResult(
            MainAppNotificationExtensionProcessLockStatus.UNAVAILABLE,
            null
        )

        is ProcessLockAcquireResult.RetryableFailure -> MainAppNotificationExtensionProcessLockResult(
            MainAppNotificationExtensionProcessLockStatus.RETRYABLE_FAILURE,
            null
        )

        is ProcessLockAcquireResult.TerminalFailure -> MainAppNotificationExtensionProcessLockResult(
            MainAppNotificationExtensionProcessLockStatus.TERMINAL_FAILURE,
            null
        )
    }

internal const val ACCOUNT_WIDE_PROCESS_LOCK_CLIENT_ID: String =
    "com.wire.kalium.notification-sync.account-wide/v1"
