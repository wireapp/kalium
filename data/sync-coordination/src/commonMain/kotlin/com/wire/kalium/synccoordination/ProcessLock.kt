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

package com.wire.kalium.synccoordination

/** Result of an immediate, non-blocking attempt to own one cross-process lock. */
public sealed interface ProcessLockAcquireResult {
    /** The returned lease owns the native resource that keeps the lock held. */
    public data class Acquired(public val lease: ProcessLockLease) : ProcessLockAcquireResult

    /** Another process currently owns the same lock. The caller must not wait or poll. */
    public data object Unavailable : ProcessLockAcquireResult

    /** A later synchronization invocation may safely try again. */
    public data class RetryableFailure(
        public val reason: RetryableProcessLockFailure
    ) : ProcessLockAcquireResult

    /** The caller or installation must change before acquisition can succeed. */
    public data class TerminalFailure(
        public val reason: TerminalProcessLockFailure
    ) : ProcessLockAcquireResult
}

public enum class RetryableProcessLockFailure {
    INTERRUPTED,
    STORAGE_EXHAUSTED,
    PROCESS_RESOURCE_EXHAUSTED,
    SYSTEM_RESOURCE_EXHAUSTED,
    IO_FAILURE
}

public enum class TerminalProcessLockFailure {
    INVALID_SHARED_ROOT,
    INVALID_LOCK_IDENTITY,
    PERMISSION_DENIED,
    READ_ONLY_FILE_SYSTEM,
    UNSAFE_FILE_SYSTEM_ENTRY,
    LOCKING_NOT_SUPPORTED,
    UNEXPECTED_PLATFORM_FAILURE
}

/**
 * Owns a cross-process lock until [release] or process termination.
 *
 * Implementations must keep the native lock resource open, and [release] must be bounded,
 * idempotent, non-blocking, and non-throwing.
 */
public fun interface ProcessLockLease {
    public fun release()
}
