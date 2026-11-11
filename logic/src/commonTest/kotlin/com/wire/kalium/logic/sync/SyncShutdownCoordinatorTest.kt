/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncShutdownCoordinatorTest {
    @Test
    fun givenMultipleAccounts_whenStopping_thenWaitForAllBatchesAndRejectNewSync() = runTest {
        val coordinator = SyncShutdownCoordinator()
        val firstBatch = CompletableDeferred<Unit>()
        val secondBatch = CompletableDeferred<Unit>()
        var firstFinished = false
        var secondFinished = false
        launch {
            coordinator.runUnlessStopping {
                withContext(NonCancellable) {
                    firstBatch.await()
                    firstFinished = true
                }
                awaitCancellation()
            }
        }
        launch {
            coordinator.runUnlessStopping {
                withContext(NonCancellable) {
                    secondBatch.await()
                    secondFinished = true
                }
                awaitCancellation()
            }
        }
        runCurrent()
        val stopping = launch { coordinator.stopForRestart() }
        runCurrent()
        assertFalse(stopping.isCompleted)
        var newSyncStarted = false
        coordinator.runUnlessStopping { newSyncStarted = true }
        assertFalse(newSyncStarted)
        firstBatch.complete(Unit)
        runCurrent()
        assertTrue(firstFinished)
        assertFalse(stopping.isCompleted)
        secondBatch.complete(Unit)
        stopping.join()
        assertTrue(secondFinished)
        coordinator.stopForRestart()
        coordinator.runUnlessStopping { newSyncStarted = true }
        assertFalse(newSyncStarted)
    }
}
