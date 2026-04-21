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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Factory used with [NomadCryptoStateChangeHookNotifier]:
 *
 * ```
 * val notifier = createNomadCryptoStateChangeHookNotifier(...)
 * ```
 */
public fun createNomadCryptoStateChangeHookNotifier(
    scope: CoroutineScope,
    backupForUser: suspend (UserId) -> Unit,
    debounceMs: Long = 1000L,
): CryptoStateChangeHookNotifier =
    createNomadCryptoStateChangeHookNotifierInternal(
        scope = scope,
        backupForUser = backupForUser,
        debounceMs = debounceMs,
    )

/**
 * Creates a crypto-state hook that is permanently bound to a single user session.
 *
 * This is the variant Logic should use when wiring Nomad from [com.wire.kalium.logic.feature.UserSessionScope]:
 * the hook ignores signals for other users and only debounces work for [selfUserId].
 */
public fun createUserScopedNomadCryptoStateChangeHookNotifier(
    selfUserId: UserId,
    scope: CoroutineScope,
    backup: suspend () -> Unit,
    debounceMs: Long = 1000L,
): CryptoStateChangeHookNotifier =
    UserScopedNomadCryptoStateChangeHookNotifier(
        selfUserId = selfUserId,
        scope = scope,
        backup = backup,
        debounceMs = debounceMs,
    )

internal fun createNomadCryptoStateChangeHookNotifierInternal(
    scope: CoroutineScope,
    backupForUser: suspend (UserId) -> Unit,
    debounceMs: Long = 1000L,
): CryptoStateChangeHookNotifier {
    val repository = NomadCryptoStateBackupDataSource(backupForUser)
    return NomadCryptoStateChangeHookNotifier(
        scope = scope,
        repository = repository,
        debounceMs = debounceMs,
    )
}

internal class UserScopedNomadCryptoStateChangeHookNotifier(
    private val selfUserId: UserId,
    private val scope: CoroutineScope,
    private val backup: suspend () -> Unit,
    private val debounceMs: Long,
) : CryptoStateChangeHookNotifier {

    private val mutex = Mutex()
    private var workerJob: Job? = null
    private var isRunning = false
    private var hasPendingChange = false

    override suspend fun onCryptoStateChanged(userId: UserId) {
        if (userId != selfUserId) {
            return
        }

        mutex.withLock {
            hasPendingChange = true
            if (isRunning) return
            workerJob?.cancel()
            workerJob = launchDebouncedWorker()
        }
    }

    private fun launchDebouncedWorker(): Job = scope.launch {
        try {
            delay(debounceMs)
            drainPendingBackups(coroutineContext.job)
        } catch (e: CancellationException) {
            // Scope is shutting down (e.g. logout): flush any pending change before stopping.
            if (!scope.isActive) {
                withContext(NonCancellable) { flushIfPending() }
            }
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun flushIfPending() {
        val shouldBackup = mutex.withLock {
            val pending = hasPendingChange
            hasPendingChange = false
            isRunning = false
            workerJob = null
            pending
        }
        if (shouldBackup) {
            try {
                backup()
            } catch (exception: Exception) {
                kaliumLogger.w("User-scoped crypto state change hook execution failed", exception)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun drainPendingBackups(currentJob: Job) {
        while (true) {
            val shouldRun = mutex.withLock {
                if (workerJob != currentJob) return
                if (!hasPendingChange) {
                    workerJob = null
                    return
                }

                hasPendingChange = false
                isRunning = true
                true
            }

            if (!shouldRun) return

            try {
                withContext(NonCancellable) {
                    backup()
                }
            } catch (exception: Exception) {
                kaliumLogger.w("User-scoped crypto state change hook execution failed", exception)
            }

            val shouldRerunImmediately = mutex.withLock {
                if (workerJob != currentJob) return

                isRunning = false
                hasPendingChange
            }

            if (!shouldRerunImmediately) {
                mutex.withLock {
                    if (workerJob == currentJob && !isRunning && !hasPendingChange) {
                        workerJob = null
                    }
                }
                return
            }
        }
    }
}
