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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NomadCryptoStateChangeHookNotifierTest {

    @Test
    fun givenSingleSignal_whenDebounceCompletes_thenBackupInvokedOnce() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<UserId>()
        val notifier = NomadCryptoStateChangeHookNotifier(
            scope = scope,
            backupForUser = { calls += it },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID), calls)
    }

    @Test
    fun givenMultipleSignals_forSameUser_whenDebounced_thenBackupInvokedOnce() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<UserId>()
        val notifier = NomadCryptoStateChangeHookNotifier(
            scope = scope,
            backupForUser = { calls += it },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        notifier.onCryptoStateChanged(USER_ID)
        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID), calls)
    }

    @Test
    fun givenMultipleSignals_forDifferentUsers_whenDebounced_thenBackupInvokedPerUser() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<UserId>()
        val notifier = NomadCryptoStateChangeHookNotifier(
            scope = scope,
            backupForUser = { calls += it },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS / 2)
        notifier.onCryptoStateChanged(OTHER_USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID, OTHER_USER_ID), calls)
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
        val USER_ID = UserId("user", "domain")
        val OTHER_USER_ID = UserId("other", "domain")
    }
}
