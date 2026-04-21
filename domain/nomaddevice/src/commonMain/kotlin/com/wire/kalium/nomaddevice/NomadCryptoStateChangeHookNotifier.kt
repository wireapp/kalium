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

package com.wire.kalium.nomaddevice

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Nomad implementation of [CryptoStateChangeHookNotifier] that debounces calls to [backupForUser] when crypto state changes for a user.
 * Handles multiple rapid calls for the same user by cancelling the previous job and starting a new one, ensuring that [backupForUser]
 * is called only once after the last change within the debounce period.
 */
internal class NomadCryptoStateChangeHookNotifier(
    private val scope: CoroutineScope,
    private val repository: NomadCryptoStateBackupRepository,
    private val debounceMs: Long = 500L,
) : CryptoStateChangeHookNotifier {

    private val mutex = Mutex()
    private val userStates = mutableMapOf<UserId, UserBackupState>()

    override suspend fun onCryptoStateChanged(userId: UserId) {
        mutex.withLock {
            val state = userStates.getOrPut(userId) { UserBackupState() }
            state.hasPendingChange = true
            if (state.isRunning) return

            state.workerJob?.cancel()
            state.workerJob = launchDebouncedWorker(userId)
        }
    }

    private fun launchDebouncedWorker(userId: UserId): Job = scope.launch {
        delay(debounceMs)
        drainPendingBackups(userId, coroutineContext.job)
    }

    private suspend fun drainPendingBackups(userId: UserId, workerJob: Job) {
        while (true) {
            val shouldRun = mutex.withLock {
                val state = userStates[userId] ?: return
                if (state.workerJob != workerJob) return
                if (!state.hasPendingChange) {
                    userStates.remove(userId)
                    return
                }

                state.hasPendingChange = false
                state.isRunning = true
                true
            }

            if (!shouldRun) return

            withContext(NonCancellable) {
                repository.backupAndUpload(userId)
            }

            val shouldRerunImmediately = mutex.withLock {
                val state = userStates[userId] ?: return
                if (state.workerJob != workerJob) return

                state.isRunning = false
                state.hasPendingChange
            }

            if (!shouldRerunImmediately) {
                mutex.withLock {
                    val state = userStates[userId]
                    if (state != null && state.workerJob == workerJob && !state.isRunning && !state.hasPendingChange) {
                        userStates.remove(userId)
                    }
                }
                return
            }
        }
    }

    private class UserBackupState(
        var workerJob: Job? = null,
        var isRunning: Boolean = false,
        var hasPendingChange: Boolean = false,
    )
}
