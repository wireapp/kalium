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

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        try {
            delay(debounceMs)
            drainPendingBackups(userId, coroutineContext.job)
        } catch (exception: CancellationException) {
            // Scope is shutting down (e.g. logout): flush any pending change before stopping.
            if (!scope.isActive) {
                withContext(NonCancellable) { flushIfPending(userId) }
            }
            throw exception
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun flushIfPending(userId: UserId) {
        val shouldBackup = mutex.withLock {
            val state = userStates[userId] ?: return

            val pending = state.hasPendingChange
            if (!pending) {
                userStates.remove(userId)
                return
            }

            userStates.remove(userId)
            pending
        }
        if (shouldBackup) {
            try {
                repository.backupAndUpload(userId)
            } catch (exception: Exception) {
                kaliumLogger.w("Multi-user crypto state change hook execution failed", exception)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun drainPendingBackups(userId: UserId, workerJob: Job) {
        var shouldContinue = true
        while (shouldContinue) {
            val shouldRun = mutex.withLock {
                preparePendingBackupRun(userId, workerJob)
            }

            if (shouldRun) {
                try {
                    withContext(NonCancellable) {
                        repository.backupAndUpload(userId)
                    }
                } catch (exception: Exception) {
                    kaliumLogger.w("Multi-user crypto state change hook execution failed", exception)
                }
            }

            shouldContinue = shouldRun && mutex.withLock {
                finishPendingBackupRun(userId, workerJob)
            }
        }
    }

    private fun preparePendingBackupRun(userId: UserId, workerJob: Job): Boolean {
        val state = userStates[userId]
        return when {
            state == null -> false
            !state.isOwnedBy(workerJob) -> false
            !state.hasPendingChange -> {
                userStates.remove(userId)
                false
            }
            else -> {
                state.hasPendingChange = false
                state.isRunning = true
                true
            }
        }
    }

    private fun finishPendingBackupRun(userId: UserId, workerJob: Job): Boolean {
        val state = userStates[userId]
        return when {
            state == null -> false
            !state.isOwnedBy(workerJob) -> false
            else -> {
                state.isRunning = false
                if (state.hasPendingChange) {
                    true
                } else {
                    cleanupUserStateIfIdle(userId, workerJob)
                    false
                }
            }
        }
    }

    private fun cleanupUserStateIfIdle(userId: UserId, workerJob: Job) {
        if (userStates[userId]?.canBeRemovedFor(workerJob) == true) {
            userStates.remove(userId)
        }
    }

    private class UserBackupState(
        var workerJob: Job? = null,
        var isRunning: Boolean = false,
        var hasPendingChange: Boolean = false,
    ) {
        fun isOwnedBy(workerJob: Job): Boolean = this.workerJob == workerJob

        fun canBeRemovedFor(workerJob: Job): Boolean = isOwnedBy(workerJob) && !isRunning && !hasPendingChange
    }
}
