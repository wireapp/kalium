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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Nomad implementation of [CryptoStateChangeHookNotifier] that debounces calls to [backupForUser] when crypto state changes for a user.
 * Handles multiple rapid calls for the same user by cancelling the previous job and starting a new one, ensuring that [backupForUser]
 * is called only once after the last change within the debounce period.
 */
public class NomadCryptoStateChangeHookNotifier(
    private val scope: CoroutineScope,
    private val backupForUser: suspend (UserId) -> Unit,
    private val debounceMs: Long = 500L,
) : CryptoStateChangeHookNotifier {

    private val mutex = Mutex()
    private val debounceJobs = mutableMapOf<UserId, Job>()

    override suspend fun onCryptoStateChanged(userId: UserId) {
        mutex.withLock {
            debounceJobs[userId]?.cancel()
            debounceJobs[userId] = scope.launch {
                delay(debounceMs)
                backupForUser(userId)
                mutex.withLock {
                    debounceJobs.remove(userId)
                }
            }
        }
    }
}
