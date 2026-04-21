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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
            repository = FakeRepository(calls),
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
            repository = FakeRepository(calls),
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
            repository = FakeRepository(calls),
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS / 2)
        notifier.onCryptoStateChanged(OTHER_USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID, OTHER_USER_ID), calls)
    }

    @Test
    fun givenFactory_whenDebounceCompletes_thenBackupInvoked() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<UserId>()
        val notifier = createNomadCryptoStateChangeHookNotifier(
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
    fun givenBackupAlreadyRunning_whenNewSignalArrives_thenSecondExecutionRunsAfterFirstCompletes() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<UserId>()
        val firstBackupStarted = CompletableDeferred<Unit>()
        val allowFirstBackupToFinish = CompletableDeferred<Unit>()
        val notifier = NomadCryptoStateChangeHookNotifier(
            scope = scope,
            repository = object : NomadCryptoStateBackupRepository {
                override suspend fun backupAndUpload(userId: UserId) {
                    calls += userId
                    if (calls.size == 1) {
                        firstBackupStarted.complete(Unit)
                        allowFirstBackupToFinish.await()
                    }
                }
            },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()
        firstBackupStarted.await()

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID), calls)

        allowFirstBackupToFinish.complete(Unit)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID, USER_ID), calls)
    }

    @Test
    fun givenBackupAlreadyRunning_whenMultipleSignalsArrive_thenQueuedSignalsCollapseIntoOneExecution() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<UserId>()
        val firstBackupStarted = CompletableDeferred<Unit>()
        val allowFirstBackupToFinish = CompletableDeferred<Unit>()
        val notifier = NomadCryptoStateChangeHookNotifier(
            scope = scope,
            repository = object : NomadCryptoStateBackupRepository {
                override suspend fun backupAndUpload(userId: UserId) {
                    calls += userId
                    if (calls.size == 1) {
                        firstBackupStarted.complete(Unit)
                        allowFirstBackupToFinish.await()
                    }
                }
            },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()
        firstBackupStarted.await()

        notifier.onCryptoStateChanged(USER_ID)
        notifier.onCryptoStateChanged(USER_ID)
        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS * 3)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID), calls)

        allowFirstBackupToFinish.complete(Unit)
        scheduler.runCurrent()

        assertEquals(listOf(USER_ID, USER_ID), calls)
    }

    @Test
    fun givenUserScopedFactory_whenOtherUserChanges_thenBackupIsIgnored() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<String>()
        val notifier = createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = USER_ID,
            scope = scope,
            backup = { calls += "backup" },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(OTHER_USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()

        assertEquals(emptyList(), calls)
    }

    @Test
    fun givenUserScopedFactory_whenScopeCancelledDuringDebounce_thenPendingBackupIsFlushed() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<String>()
        val notifier = createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = USER_ID,
            scope = scope,
            backup = { calls += "backup" },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS / 2) // mid-debounce
        scope.cancel() // simulate logout
        scheduler.runCurrent()

        assertEquals(listOf("backup"), calls)
    }

    @Test
    fun givenUserScopedFactory_whenScopeCancelledDuringBackup_thenBackupCompletes() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<String>()
        val notifier = createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = USER_ID,
            scope = scope,
            backup = {
                scope.cancel() // simulate logout mid-upload
                delay(1) // suspension point that CancellationException would abort without NonCancellable
                calls += "backup"
            },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()

        assertEquals(listOf("backup"), calls)
    }

    @Test
    fun givenUserScopedFactory_whenBackupThrows_thenExceptionIsSwallowed() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val notifier = createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = USER_ID,
            scope = scope,
            backup = { throw IllegalStateException("boom") },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()
    }

    @Test
    fun givenUserScopedFactory_whenBackupFails_thenNextTriggerRetriesBackup() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<String>()
        var shouldFail = true
        val notifier = createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = USER_ID,
            scope = scope,
            backup = {
                if (shouldFail) throw IllegalStateException("boom")
                calls += "backup"
            },
            debounceMs = DEBOUNCE_MS
        )

        // First trigger — backup fails, flag should remain true
        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()
        assertEquals(emptyList(), calls)

        // Second trigger — backup succeeds this time
        shouldFail = false
        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()

        assertEquals(listOf("backup"), calls)
    }

    @Test
    fun givenUserScopedFactory_whenBackupRunningAndNewSignalArrives_thenSecondExecutionRunsAfterFirstCompletes() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<String>()
        val firstBackupStarted = CompletableDeferred<Unit>()
        val allowFirstBackupToFinish = CompletableDeferred<Unit>()
        val notifier = createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = USER_ID,
            scope = scope,
            backup = {
                calls += "backup"
                if (calls.size == 1) {
                    firstBackupStarted.complete(Unit)
                    allowFirstBackupToFinish.await()
                }
            },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()
        firstBackupStarted.await()

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.runCurrent()

        assertEquals(listOf("backup"), calls)

        allowFirstBackupToFinish.complete(Unit)
        scheduler.runCurrent()

        assertEquals(listOf("backup", "backup"), calls)
    }

    @Test
    fun givenUserScopedFactory_whenMultipleSignalsArriveDuringRunningBackup_thenQueuedSignalsCollapseIntoOneExecution() = runTest {
        val scheduler = TestCoroutineScheduler()
        val scope = TestScope(scheduler)
        val calls = mutableListOf<String>()
        val firstBackupStarted = CompletableDeferred<Unit>()
        val allowFirstBackupToFinish = CompletableDeferred<Unit>()
        val notifier = createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = USER_ID,
            scope = scope,
            backup = {
                calls += "backup"
                if (calls.size == 1) {
                    firstBackupStarted.complete(Unit)
                    allowFirstBackupToFinish.await()
                }
            },
            debounceMs = DEBOUNCE_MS
        )

        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS + 1)
        scheduler.runCurrent()
        firstBackupStarted.await()

        notifier.onCryptoStateChanged(USER_ID)
        notifier.onCryptoStateChanged(USER_ID)
        notifier.onCryptoStateChanged(USER_ID)
        scheduler.advanceTimeBy(DEBOUNCE_MS * 3)
        scheduler.runCurrent()

        assertEquals(listOf("backup"), calls)

        allowFirstBackupToFinish.complete(Unit)
        scheduler.runCurrent()

        assertEquals(listOf("backup", "backup"), calls)
    }

    private class FakeRepository(
        private val calls: MutableList<UserId>
    ) : NomadCryptoStateBackupRepository {
        override suspend fun backupAndUpload(userId: UserId) {
            calls += userId
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
        val USER_ID = UserId("user", "domain")
        val OTHER_USER_ID = UserId("other", "domain")
    }
}
