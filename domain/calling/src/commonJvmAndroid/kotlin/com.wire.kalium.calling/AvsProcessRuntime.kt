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

package com.wire.kalium.calling

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.LogHandler

/** Selects the native AVS process startup sequence required by the current platform. */
enum class AvsProcessMode {
    ANDROID,
    DESKTOP,
}

/**
 * One reference to the process-global AVS runtime.
 *
 * AVS itself is process-global even though each Wire identity owns a separate call handle. Keeping
 * this lease outside either top-level composition prevents the client and service from starting or
 * closing the same native runtime independently.
 */
class AvsProcessLease internal constructor(
    val calling: Calling,
    private val logHandler: LogHandler?,
) : AutoCloseable {
    private var closed: Boolean = false

    override fun close() {
        synchronized(this) {
            if (closed) return
            AvsProcessRuntime.release(calling, logHandler)
            closed = true
        }
    }
}

/** Reference-counted owner of the process-global AVS setup, run, logging, and close lifecycle. */
object AvsProcessRuntime {
    private const val AVS_SUCCESS = 0
    private val monitor = Any()
    private val logHandlers = linkedSetOf<LogHandler>()
    private var activeCalling: Calling? = null
    private var activeMode: AvsProcessMode? = null
    private var users: Int = 0

    /** Starts AVS for the first user and returns a lease that must be retained while AVS is used. */
    fun acquire(
        calling: Calling = Calling.INSTANCE,
        mode: AvsProcessMode,
        logHandler: LogHandler? = null,
    ): AvsProcessLease = synchronized(monitor) {
        if (users == 0) {
            start(calling, mode)
            activeCalling = calling
            activeMode = mode
            calling.wcall_set_log_handler(SharedLogHandler, null)
        } else {
            check(activeCalling === calling) { "AVS is already owned through a different native binding" }
            check(activeMode == mode) { "AVS is already running with a different platform startup mode" }
        }
        logHandler?.let(logHandlers::add)
        users += 1
        AvsProcessLease(calling, logHandler)
    }

    internal fun release(calling: Calling, logHandler: LogHandler?): Unit = synchronized(monitor) {
        check(users > 0 && activeCalling === calling) { "AVS process lease is not active" }
        if (users == 1) {
            // Mutate ownership only after native close succeeds so callers can retry a failed close.
            calling.wcall_close()
            users = 0
            activeCalling = null
            activeMode = null
            logHandlers.clear()
        } else {
            users -= 1
            logHandler?.let(logHandlers::remove)
        }
    }

    private fun start(calling: Calling, mode: AvsProcessMode) {
        when (mode) {
            AvsProcessMode.ANDROID -> calling.wcall_init(ENVIRONMENT_DEFAULT)
            AvsProcessMode.DESKTOP -> {
                val setup = calling.wcall_setup()
                if (setup != AVS_SUCCESS) {
                    calling.wcall_close()
                    error("AVS setup failed with code $setup")
                }
                val run = calling.wcall_run()
                if (run != AVS_SUCCESS) {
                    calling.wcall_close()
                    error("AVS run failed with code $run")
                }
            }
        }
    }

    private object SharedLogHandler : LogHandler {
        override fun onLog(level: Int, message: String, arg: Pointer?) {
            val currentHandlers = synchronized(monitor) { logHandlers.toList() }
            currentHandlers.forEach { handler -> handler.onLog(level, message, arg) }
        }
    }
}
