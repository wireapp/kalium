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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private var debounceJob: Job? = null

    override suspend fun onCryptoStateChanged(userId: UserId) {
        if (userId != selfUserId) {
            return
        }

        mutex.withLock {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceMs)
                try {
                    backup()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    kaliumLogger.w("User-scoped crypto state change hook execution failed", exception)
                } finally {
                    mutex.withLock {
                        if (debounceJob == this) {
                            debounceJob = null
                        }
                    }
                }
            }
        }
    }
}
