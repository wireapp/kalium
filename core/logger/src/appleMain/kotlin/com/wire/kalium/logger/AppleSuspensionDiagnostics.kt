/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logger

import co.touchlab.kermit.darwin.kermit_darwin_log_create
import co.touchlab.kermit.darwin.kermit_darwin_log_public_with_type
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLock
import platform.Foundation.NSProcessInfo
import platform.darwin.OS_LOG_TYPE_DEFAULT
import platform.posix.getpid

/**
 * Bounded, process-local observations for investigating suspension with shared files open.
 *
 * This writes directly to Apple's unified log, never through a file logger. Labels use a closed vocabulary;
 * callers must not pass paths, SQL, account identities or exception messages. Unknown labels become `other`.
 * Database spans measure API operations, not SQLite VFS locks. A lifecycle stage does not establish that the
 * process may execute, that it has suspended, or that its files are safe for suspension. Delivery is best effort.
 */
@OptIn(ExperimentalForeignApi::class)
public object AppleSuspensionDiagnostics {
    private val nativeLog = kermit_darwin_log_create("com.wire.prism.ios", "SuspensionDiagnostics")
    private val registry = SuspensionDiagnosticRegistry(
        pid = getpid(),
        run = AppleDiagnosticProcess.id,
        clockMillis = { (NSProcessInfo.processInfo.systemUptime * MILLIS_PER_SECOND).toLong() },
        emit = { line -> nativeLog?.let { kermit_darwin_log_public_with_type(it, OS_LOG_TYPE_DEFAULT, line) } },
    )

    public fun beginLock(kind: String, operation: String, descriptor: Int = -1): Long =
        registry.beginLock(kind, operation, descriptor)

    /** POSIX result zero means acquired; a failed attempt never establishes ownership. */
    public fun acquired(token: Long, result: Int, error: Int = 0) = registry.acquired(token, result, error)
    public fun unlocked(token: Long, result: Int, error: Int = 0) = registry.unlocked(token, result, error)

    /** A failed or interrupted close leaves UNKNOWN; callers must not retry close for diagnostics. */
    public fun closed(token: Long, result: Int, error: Int = 0) = registry.closed(token, result, error)

    /** Retire an unsuccessful attempt on a descriptor that stays open. Never clears HELD or UNKNOWN. */
    public fun abandoned(token: Long) = registry.abandoned(token)
    public fun beginDatabaseOperation(databaseKind: String, operation: String): Long =
        registry.beginDatabaseOperation(databaseKind, operation)
    public fun endDatabaseOperation(token: Long, outcome: String) = registry.endDatabaseOperation(token, outcome)
    public fun setBackgrounded(backgrounded: Boolean) = registry.setBackgrounded(backgrounded)
    public fun snapshot(stage: String) = registry.snapshot(stage)
}

/** No filesystem operation, SQL operation or emitter runs while [lock] is held. */
@Suppress("TooManyFunctions") // One small synchronized registry owns all observation state transitions.
internal class SuspensionDiagnosticRegistry(
    private val pid: Int,
    private val run: String,
    private val clockMillis: () -> Long,
    private val emit: (String) -> Unit,
    private val capacity: Int = DEFAULT_REGISTRY_CAPACITY,
    private val eventLimitPerSecond: Int = DEFAULT_EVENT_LIMIT,
) {
    private val lock = NSLock()
    private val entries = mutableMapOf<Long, Entry>()
    private val descriptorOwners = mutableMapOf<Int, Long>()
    private var nextToken = 0L
    private var sequence = 0L
    private var backgrounded = false
    private var gaps = 0L
    private var overflow = 0L
    private var quiet = 0L
    private var throttled = 0L
    private var emitFailures = 0L
    private var windowStart = 0L
    private var windowCount = 0

    fun beginLock(kind: String, operation: String, descriptor: Int): Long =
        begin(kind.safe(KINDS), operation.safe(OPERATIONS), descriptor, database = false)

    fun beginDatabaseOperation(kind: String, operation: String): Long =
        begin(kind.safe(DATABASE_KINDS), operation.safe(OPERATIONS), -1, database = true)

    private fun begin(kind: String, operation: String, descriptor: Int, database: Boolean): Long {
        var token = 0L
        val line = locked {
            val now = clockMillis()
            if (entries.size >= capacity || nextToken == Long.MAX_VALUE) {
                overflow++
                event(now, "overflow", force = true)
            } else {
                token = ++nextToken
                val entry = Entry(token, kind, operation, descriptor, now, if (database) "active" else "attempted", database)
                if (!database && descriptor >= 0) {
                    descriptorOwners[descriptor]?.let { previous ->
                        entries[previous]?.let {
                            // The OS may reuse an fd before its previous owner's close callback is recorded.
                            if (it.state != "unknown") gaps++
                            it.state = "unknown"
                        }
                    }
                    descriptorOwners[descriptor] = token
                }
                entries[token] = entry
                event(now, "begin", entry)
            }
        }
        deliver(line)
        return token
    }

    fun acquired(token: Long, result: Int, error: Int) = updateLock(token, "acquired", result, error) { entry ->
        if (result == 0 && (entry.descriptor < 0 || descriptorOwners[entry.descriptor] == token)) entry.state = "held"
        else if (result == 0) {
            entry.state = "unknown"
            gaps++
        } else if (entry.state != "attempted") {
            entry.state = "unknown"
            gaps++
        }
    }

    fun unlocked(token: Long, result: Int, error: Int) = updateLock(token, "unlocked", result, error) { entry ->
        if (result == 0) {
            entry.state = "not_held"
            remove(entry)
        } else entry.state = "unknown"
    }

    fun closed(token: Long, result: Int, error: Int) = updateLock(token, "closed", result, error) { entry ->
        if (result == 0) {
            entry.state = "not_held"
            remove(entry)
        } else entry.state = "unknown"
    }

    private fun updateLock(token: Long, action: String, result: Int, error: Int, change: (Entry) -> Unit) {
        if (token == 0L) return
        val line = locked {
            val now = clockMillis()
            val entry = entries[token]
            if (entry == null || entry.database) {
                gaps++
                event(now, "unmatched_$action", force = true, extra = "token=$token result=$result errno=$error")
            } else {
                change(entry)
                event(
                    now,
                    action,
                    entry,
                    force = result != 0 || now - entry.started >= LONG_OPERATION_MILLIS,
                    extra = "result=$result errno=$error"
                )
            }
        }
        deliver(line)
    }

    fun abandoned(token: Long) {
        if (token == 0L) return
        val line = locked {
            val now = clockMillis()
            val entry = entries[token]
            if (entry != null && !entry.database && entry.state == "attempted") {
                remove(entry)
                entry.state = "not_held"
                event(now, "abandoned", entry)
            } else {
                gaps++
                event(now, "unmatched_abandon", entry, force = true, extra = "token=$token")
            }
        }
        deliver(line)
    }

    fun endDatabaseOperation(token: Long, outcome: String) {
        if (token == 0L) return
        val safeOutcome = outcome.safe(OUTCOMES)
        val line = locked {
            val now = clockMillis()
            val entry = entries[token]
            if (entry == null || !entry.database) {
                gaps++
                event(now, "unmatched_database_end", force = true, extra = "token=$token")
            } else {
                entries.remove(token)
                entry.state = "completed"
                event(
                    now,
                    "database_end",
                    entry,
                    force = safeOutcome !in SUCCESS_OUTCOMES || now - entry.started >= LONG_OPERATION_MILLIS,
                    extra = "outcome=$safeOutcome"
                )
            }
        }
        deliver(line)
    }

    fun setBackgrounded(value: Boolean) {
        locked { backgrounded = value }
    }

    fun snapshot(stage: String) {
        val safeStage = stage.safe(STAGES)
        val lines = locked {
            val now = clockMillis()
            val snapshot = ++sequence
            val values = entries.values.toList()
            val summary = prefix(now, snapshot) + " event=snapshot stage=$safeStage snapshot=$snapshot" +
                " backgrounded=$backgrounded outstanding=${values.size}" +
                " attempted=${values.count { !it.database && it.state == "attempted" }}" +
                " held=${values.count { !it.database && it.state == "held" }}" +
                " unknown=${values.count { !it.database && it.state == "unknown" }}" +
                " userOperations=${values.count { it.database && it.kind == "user_database" && it.operation != "transaction" }}" +
                " globalOperations=${values.count { it.database && it.kind == "global_database" && it.operation != "transaction" }}" +
                " transactions=${values.count { it.database && it.operation == "transaction" }}" +
                " gaps=$gaps overflow=$overflow quiet=$quiet throttled=$throttled emitFailures=$emitFailures" +
                " sqliteVfsLocks=unobserved delivery=best_effort"
            listOf(summary) + values.map { entry ->
                prefix(now, ++sequence) + " event=outstanding snapshot=$snapshot ${fields(entry, now)}"
            }
        }
        lines.forEach(::deliver)
    }

    private fun event(now: Long, action: String, entry: Entry? = null, force: Boolean = false, extra: String = ""): String? {
        val seq = ++sequence
        return if (!backgrounded && !force) {
            quiet++
            null
        } else {
            if (now - windowStart >= MILLIS_PER_SECOND || now < windowStart) {
                windowStart = now
                windowCount = 0
            }
            if (windowCount++ >= eventLimitPerSecond) {
                throttled++
                null
            } else {
                prefix(now, seq) + " event=$action" + (entry?.let { " ${fields(it, now)}" } ?: "") +
                    if (extra.isEmpty()) "" else " $extra"
            }
        }
    }

    private fun remove(entry: Entry) {
        entries.remove(entry.token)
        if (!entry.database && descriptorOwners[entry.descriptor] == entry.token) {
            descriptorOwners.remove(entry.descriptor)
        }
    }

    private fun fields(entry: Entry, now: Long): String = entry.fields(now) +
        if (entry.database) "" else " fdCurrent=${descriptorOwners[entry.descriptor] == entry.token}"

    private fun prefix(now: Long, seq: Long): String =
        "SUSPENSION_DIAGNOSTICS schema=1 pid=$pid run=$run monoMs=$now seq=$seq"

    private fun deliver(line: String?) {
        if (line != null) try {
            emit(line)
        } catch (_: Exception) {
            locked { emitFailures++ }
        }
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    @Suppress("LongParameterList") // Each field is an independent part of a bounded observation record.
    private class Entry(
        val token: Long,
        val kind: String,
        val operation: String,
        val descriptor: Int,
        val started: Long,
        var state: String,
        val database: Boolean,
    ) {
        fun fields(now: Long): String = "token=$token kind=$kind operation=$operation fd=$descriptor state=$state" +
            " ageMs=${(now - started).coerceAtLeast(0)}"
    }

    private companion object {
        const val LONG_OPERATION_MILLIS = 1_000L
        const val DEFAULT_REGISTRY_CAPACITY = 256
        const val DEFAULT_EVENT_LIMIT = 64
        val KINDS = setOf("account_runtime", "diagnostic_logging", "kalium_diagnostic_logging")
        val DATABASE_KINDS = setOf("user_database", "global_database", "other_database")
        val OPERATIONS = setOf(
            "ownership", "append", "append_batch", "maintenance", "checkpoint", "snapshot", "delete",
            "read", "write", "transaction", "open", "close"
        )
        val SUCCESS_OUTCOMES = setOf("success", "commit", "rollback", "deferred")
        val OUTCOMES = SUCCESS_OUTCOMES + setOf("failure", "cancelled", "unknown", "post_commit_failure")
        val STAGES = setOf(
            "CONTAINER_READY", "FOREGROUND_REQUEST", "FOREGROUND_BEGIN", "FOREGROUND_OPENED",
            "FOREGROUND_CANCELLED", "FOREGROUND_FAILED", "BACKGROUND_REQUEST", "BACKGROUND_TASK_GRANTED",
            "BACKGROUND_TASK_DENIED", "BACKGROUND_TASK_EXPIRED", "BACKGROUND_TASK_FINISH", "WAIT_PREVIOUS_TRANSITION",
            "DRAIN_NOTIFICATION_OBSERVERS", "KALIUM_HANDOFF_BEGIN", "KALIUM_HANDOFF_RETURNED_TRUE", "KALIUM_HANDOFF_RETAINED",
            "KALIUM_HANDOFF_CANCELLED", "KALIUM_HANDOFF_FAILED", "SCENE_active", "SCENE_inactive", "SCENE_background",
            "SCENE_ACTIVE", "SCENE_INACTIVE", "SCENE_BACKGROUND", "CALLKIT_VIDEO_ACTIVITY_RECEIVED",
            "CALLKIT_VIDEO_ACTIVITY_UNRECOGNIZED", "PROTECTED_DATA_AVAILABLE", "PROTECTED_DATA_WILL_LOCK",
            "MEMORY_WARNING", "NSE_BEGIN", "NSE_END", "TEST"
        )
        fun String.safe(vocabulary: Set<String>): String = if (this in vocabulary) this else "other"
    }
}

private const val MILLIS_PER_SECOND = 1_000L
