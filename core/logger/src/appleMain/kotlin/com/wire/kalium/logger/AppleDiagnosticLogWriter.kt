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

package com.wire.kalium.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import platform.Foundation.NSLock
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock

/**
 * Opt-in, app-lifetime file writer shared by Apple hosts. The host owns preferences and logger configuration.
 * Use distinct stream names for the app and extensions. All instances sharing a root coordinate through flock.
 *
 * Writes are best effort: a full submission queue, unavailable storage, or another process holding the lock drops
 * the entry. Entries over 32K message/tag characters and queues over 256K characters are also dropped.
 * Throwable references are retained until processing; their stack traces are formatted only on the IO queue.
 * Lock acquisition never waits. Accepted entries close their sinks before the next queue operation.
 * File IO itself can stall; extension completion must use [flushLogsWithin], never an unbounded flush.
 */
@OptIn(ExperimentalForeignApi::class)
public class AppleDiagnosticLogWriter(
    rootPath: String,
    streamName: String = "app",
    diagnosticLoggingEnabled: Boolean = false,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    rolledFiles: Int = DEFAULT_ROLLED_FILES,
) : LogWriter() {
    private val storage = AppleDiagnosticLogStorage(rootPath, streamName, fileSystem, maxFileBytes, rolledFiles)
    private val submissionLock = NSLock()
    private val queue = dispatch_queue_create("com.wire.kalium.diagnostic-logging.$streamName", null)
    private var enabled = diagnosticLoggingEnabled
    private var pendingEntries = 0
    private var pendingCharacters = 0
    private var generation = 0L

    public val isLoggingEnabled: Boolean get() = locked { enabled }
    public val displayPath: String get() = storage.displayPath

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        locked {
            val characters = message.length.toLong() + tag.length
            if (!enabled || pendingEntries >= MAX_PENDING_ENTRIES) return@locked
            if (characters > MAX_ENTRY_CHARACTERS || pendingCharacters + characters > MAX_PENDING_CHARACTERS) return@locked
            val timestamp = Clock.System.now().toString()
            val acceptedGeneration = generation
            pendingEntries++
            pendingCharacters += characters.toInt()
            dispatch_async(queue) {
                try {
                    if (locked { acceptedGeneration == generation }) {
                        storage.append(timestamp, severity, message, tag, throwable)
                    }
                } catch (_: Exception) {
                    // Diagnostics must never fail the operation being diagnosed or poison a future export.
                } finally {
                    locked {
                        pendingEntries--
                        pendingCharacters -= characters.toInt()
                    }
                }
            }
        }
    }

    /** Refresh host-owned opt-in without IO or waiting for accepted writes. */
    public fun updateLoggingEnabled(isEnabled: Boolean) = locked { enabled = isEnabled }

    /** Stop accepting entries immediately, then finish earlier accepted entries even if the caller is cancelled. */
    public suspend fun setLoggingEnabled(isEnabled: Boolean) {
        updateLoggingEnabled(isEnabled)
        onQueue { }
    }

    /** Wait for accepted writes, reporting current storage access failures rather than historical dropped entries. */
    public suspend fun flushLogs() = onQueue { storage.checkAccess() }

    /**
     * Completion barrier with a caller-supplied budget. False means the barrier did not finish in that budget.
     * No filesystem validation is performed. Dropped entries still count as finished; this is not a durability promise.
     */
    public suspend fun flushLogsWithin(timeoutMillis: Long): Boolean = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            locked { dispatch_async(queue) { continuation.resumeWith(Result.success(Unit)) } }
        }
        true
    } ?: false

    /** Drop queued entries after expiry; an already executing filesystem operation is allowed to finish. */
    public fun discardPendingLogs() {
        locked { generation++ }
    }

    /** Copy every stream into a new private directory. Contention or storage errors are reported to the caller. */
    public suspend fun createLogsSnapshot(destinationPath: String) = onQueue { storage.snapshot(destinationPath) }

    /** Delete regular diagnostic files across all streams, retaining preferences, lock file, and unrelated files. */
    public suspend fun deleteLogs() = onQueue { storage.delete() }

    private suspend fun <T> onQueue(block: () -> T): T = suspendCoroutine { continuation ->
        locked { dispatch_async(queue) { continuation.resumeWith(runCatching(block)) } }
    }

    private inline fun <T> locked(block: () -> T): T {
        submissionLock.lock()
        return try { block() } finally { submissionLock.unlock() }
    }

    public companion object {
        public const val DEFAULT_MAX_FILE_BYTES: Long = 25 * 1024 * 1024L
        public const val DEFAULT_ROLLED_FILES: Int = 10
        private const val MAX_PENDING_ENTRIES = 64
        private const val MAX_ENTRY_CHARACTERS = 32 * 1024
        private const val MAX_PENDING_CHARACTERS = 256 * 1024
    }
}
