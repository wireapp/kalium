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
package com.wire.kalium.work

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InitialSyncWorkMonitorTest {

    @Test
    fun givenSyncBooleanFlowIsTrue_thenShouldUpdateWorkStatusToInProgress() = runTest {
        val slowSyncFlow = Channel<Boolean>()
        val repo = InMemoryWorkRepository()
        val monitor = InitialSyncWorkMonitor(backgroundScope, slowSyncFlow.consumeAsFlow(), repo)
        val workId = WorkId.INITIAL_SYNC

        monitor.startMonitoring()
        slowSyncFlow.send(true)
        advanceUntilIdle()

        repo.observeWork(workId).test {
            assertEquals(Work.Status.InProgress, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSyncBooleanFlowIsFalse_thenShouldUpdateWorkStatusToComplete() = runTest {
        val slowSyncFlow = Channel<Boolean>()
        val repo = InMemoryWorkRepository()
        val monitor = InitialSyncWorkMonitor(backgroundScope, slowSyncFlow.receiveAsFlow(), repo)
        val workId = WorkId.INITIAL_SYNC

        monitor.startMonitoring()
        slowSyncFlow.send(false)

        repo.observeWork(workId).test {
            assertEquals(Work.Status.Complete, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSyncBooleanFlowChanges_thenShouldUpdateWorkStatusAccordingly() = runTest {
        val slowSyncFlow = Channel<Boolean>()
        val repo = InMemoryWorkRepository()
        val monitor = InitialSyncWorkMonitor(backgroundScope, slowSyncFlow.receiveAsFlow(), repo)
        val workId = WorkId.INITIAL_SYNC

        monitor.startMonitoring()
        slowSyncFlow.send(false)

        repo.observeWork(workId).test {
            // Initial emission (no work yet) is Complete
            assertEquals(Work.Status.Complete, awaitItem())

            // Drive changes explicitly
            slowSyncFlow.send(true)
            assertEquals(Work.Status.InProgress, awaitItem())

            slowSyncFlow.send(false)
            assertEquals(Work.Status.Complete, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenScopeIsCancelled_thenShouldStopMonitoringAndUpdatingTheStatus() = runTest {
        val slowSyncFlow = Channel<Boolean>(capacity = Int.MAX_VALUE)
        val repo = InMemoryWorkRepository()
        val parentJob = Job()
        val monitor = InitialSyncWorkMonitor(
            CoroutineScope(backgroundScope.newCoroutineContext(parentJob)),
            slowSyncFlow.receiveAsFlow(),
            repo
        )
        val workId = WorkId.INITIAL_SYNC

        monitor.startMonitoring()
        parentJob.cancel()

        repo.observeWork(workId).test {
            // Initial emission (no work yet) is Complete
            assertEquals(Work.Status.Complete, awaitItem())

            slowSyncFlow.send(true)
            slowSyncFlow.send(false)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
