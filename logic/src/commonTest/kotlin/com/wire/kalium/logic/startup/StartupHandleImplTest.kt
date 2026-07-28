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

package com.wire.kalium.logic.startup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StartupHandleImplTest {

    @Test
    fun givenStartupHasNotRun_whenOpening_thenValueIsCachedAsReady() = runTest {
        var openingCount = 0
        val handle = StartupHandleImpl(backgroundScope) {
            openingCount++
            "ready"
        }

        val result = handle.open()

        assertEquals(1, openingCount)
        assertEquals("ready", assertIs<StartupResult.Success<String>>(result).value)
        assertEquals(StartupState.Ready, handle.state.value)
        assertEquals("ready", handle.readyOrNull())
    }

    @Test
    fun givenConcurrentCallers_whenOpening_thenBackingActionRunsOnce() = runTest {
        val continueOpening = CompletableDeferred<Unit>()
        var openingCount = 0
        val handle = StartupHandleImpl(backgroundScope) {
            openingCount++
            continueOpening.await()
            "ready"
        }

        val first = async { handle.open() }
        runCurrent()
        val second = async { handle.open() }
        runCurrent()

        assertEquals(1, openingCount)
        assertEquals(StartupState.Opening, handle.state.value)

        continueOpening.complete(Unit)

        assertEquals("ready", assertIs<StartupResult.Success<String>>(first.await()).value)
        assertEquals("ready", assertIs<StartupResult.Success<String>>(second.await()).value)
    }

    @Test
    fun givenOneCallerIsCancelled_whenOpening_thenSharedOperationContinues() = runTest {
        val continueOpening = CompletableDeferred<Unit>()
        var openingCount = 0
        val handle = StartupHandleImpl(backgroundScope) {
            openingCount++
            continueOpening.await()
            "ready"
        }

        val cancelledCaller = async { handle.open() }
        runCurrent()
        cancelledCaller.cancelAndJoin()

        val remainingCaller = async { handle.open() }
        runCurrent()
        continueOpening.complete(Unit)

        assertEquals(1, openingCount)
        assertEquals("ready", assertIs<StartupResult.Success<String>>(remainingCaller.await()).value)
        assertEquals(StartupState.Ready, handle.state.value)
    }

    @Test
    fun givenRetryableFailure_whenRetrying_thenBackingActionRunsAgain() = runTest {
        var openingCount = 0
        val handle = StartupHandleImpl(
            startupScope = backgroundScope,
            isRetryableFailure = { true },
        ) {
            openingCount++
            if (openingCount == 1) error("database is temporarily locked")
            "ready"
        }

        val firstResult = handle.open()
        val retryResult = handle.retry()

        assertIs<StartupResult.Failure>(firstResult)
        assertEquals("ready", assertIs<StartupResult.Success<String>>(retryResult).value)
        assertEquals(2, openingCount)
        assertEquals(StartupState.Ready, handle.state.value)
    }

    @Test
    fun givenPermanentFailure_whenOpeningAgain_thenFailureIsCached() = runTest {
        var openingCount = 0
        val handle = StartupHandleImpl<String>(backgroundScope) {
            openingCount++
            error("database is corrupt")
        }

        val firstResult = handle.open()
        val secondResult = handle.open()
        val retryResult = handle.retry()

        assertEquals(firstResult, secondResult)
        assertEquals(firstResult, retryResult)
        assertEquals(1, openingCount)
        assertNull(handle.readyOrNull())
    }

    @Test
    fun givenSchemaUpgradeStarts_whenOpening_thenMigrationStateIsReported() = runTest {
        val continueMigration = CompletableDeferred<Unit>()
        lateinit var handle: StartupHandleImpl<String>
        handle = StartupHandleImpl(backgroundScope) {
            handle.migrationObserver.onMigrationStarted(fromVersion = 10, toVersion = 12)
            continueMigration.await()
            handle.migrationObserver.onMigrationCompleted(fromVersion = 10, toVersion = 12)
            "ready"
        }

        val opening = async { handle.open() }
        runCurrent()

        assertEquals(
            StartupState.Migrating(MigrationProgress(MigrationProgress.Stage.UpdatingSchema)),
            handle.state.value,
        )

        continueMigration.complete(Unit)
        assertEquals("ready", assertIs<StartupResult.Success<String>>(opening.await()).value)
        assertEquals(StartupState.Ready, handle.state.value)
    }
}
