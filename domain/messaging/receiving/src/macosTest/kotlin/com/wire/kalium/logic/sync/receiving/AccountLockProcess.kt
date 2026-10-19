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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.logic.sync.receiving

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.LOCK_NB
import platform.posix.LOCK_EX
import platform.posix.O_CLOEXEC
import platform.posix.O_RDWR
import platform.posix.SIGKILL
import platform.posix._exit
import platform.posix.close
import platform.posix.flock
import platform.posix.fork
import platform.posix.kill
import platform.posix.open
import platform.posix.pipe
import platform.posix.read
import platform.posix.waitpid
import platform.posix.write

/**
 * A second real process holding the account lock; it does not model database or crypto handles.
 *
 * The child performs only prearranged POSIX calls after [fork] and exits through [_exit]. It does
 * not start coroutines or run account code in the copied native test process.
 * This checks the macOS kernel boundary. Release proof still requires an app and NSE pair against the
 * shared App Group container and its data-protection rules.
 */
internal class AccountLockProcess private constructor(
    private val processId: Int,
    private val releasePipe: Int,
) {
    private var finished = false

    fun stop() {
        if (finished) return
        close(releasePipe)
        waitpid(processId, null, 0)
        finished = true
    }

    fun terminate() {
        if (finished) return
        kill(processId, SIGKILL)
        close(releasePipe)
        waitpid(processId, null, 0)
        finished = true
    }

    companion object {
        fun start(lockFilePath: String): AccountLockProcess = memScoped {
            val descriptor = open(lockFilePath, O_RDWR or O_CLOEXEC)
            check(descriptor >= 0) { "Could not open the account lock fixture file" }

            val ready = allocArray<IntVar>(2)
            val release = allocArray<IntVar>(2)
            check(pipe(ready) == 0) { "Could not create the ready pipe" }
            check(pipe(release) == 0) { "Could not create the release pipe" }
            val signal = alloc<ByteVar>()
            signal.value = 1

            val processId = fork()
            check(processId >= 0) { "Could not fork the account runtime fixture" }

            if (processId == 0) {
                close(ready[0])
                close(release[1])
                if (flock(descriptor, LOCK_EX or LOCK_NB) != 0) _exit(CHILD_LOCK_FAILED)
                if (write(ready[1], signal.ptr, 1uL) != 1L) _exit(CHILD_SIGNAL_FAILED)
                read(release[0], signal.ptr, 1uL)
                close(descriptor)
                _exit(CHILD_SUCCESS)
            }

            close(descriptor)
            close(ready[1])
            close(release[0])
            val bytesRead = read(ready[0], signal.ptr, 1uL)
            close(ready[0])
            if (bytesRead != 1L) {
                close(release[1])
                waitpid(processId, null, 0)
                error("The child account runtime did not acquire its lock")
            }

            AccountLockProcess(processId, release[1])
        }

        private const val CHILD_SUCCESS = 0
        private const val CHILD_LOCK_FAILED = 10
        private const val CHILD_SIGNAL_FAILED = 11
    }
}
