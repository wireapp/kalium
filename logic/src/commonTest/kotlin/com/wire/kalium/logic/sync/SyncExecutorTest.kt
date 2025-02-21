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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.fakes.sync.FakeIncrementalSyncManager
import com.wire.kalium.logic.fakes.sync.FakeSlowSyncManager
import com.wire.kalium.logic.fakes.sync.FakeSyncStateObserver
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SyncExecutorTest {

    @Test
    fun givenNoRequests_whenStartingAsNeeded_thenShouldNotStartAnySync() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(backgroundScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()
        advanceUntilIdle()

        assertEquals(0, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
        assertEquals(0, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
        syncScope.cancel()
    }

    @Test
    fun givenOneRequest_whenStartingAsNeeded_thenShouldStartWithSlowSyncFirst() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        syncExecutor.request {
            advanceUntilIdle()
            assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
            assertEquals(0, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
        }
        syncScope.cancel()
    }

    @Test
    fun givenWaitingUntilLive_whenLiveState_thenShouldProceed() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        var didContinue = false
        val waitJob = backgroundScope.launch {
            syncExecutor.request {
                advanceUntilIdle()
                waitUntilLiveOrFailure().shouldSucceed()
                didContinue = true
            }
        }
        advanceUntilIdle()
        assertFalse(didContinue)
        arrangement.syncStateObserver.mutableSyncState.emit(SyncState.Live)
        waitJob.join()
        advanceUntilIdle()
        assertTrue(didContinue)
        syncScope.cancel()
    }

    @Test
    fun givenWaitingUntilPendingEvents_whenStateIsReached_thenShouldProceed() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        var didContinue = false
        val waitingJob = backgroundScope.launch {
            syncExecutor.request {
                advanceUntilIdle()
                waitUntilOrFailure(SyncState.GatheringPendingEvents).shouldSucceed()
                didContinue = true
            }
        }
        advanceUntilIdle()
        assertFalse(didContinue)
        arrangement.syncStateObserver.mutableSyncState.emit(SyncState.GatheringPendingEvents)
        advanceUntilIdle()
        waitingJob.join()
        assertTrue(didContinue)
        syncScope.cancel()
    }

    @Test
    fun givenWaitingForState_whenFailureHappens_thenShouldContinue() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()
        val expectedFailure = CoreFailure.SyncEventOrClientNotFound
        var actualFailure: CoreFailure? = null
        var didContinue = false

        val waitingJob = backgroundScope.launch {
            syncExecutor.request {
                advanceUntilIdle()
                val result = waitUntilOrFailure(SyncState.GatheringPendingEvents)
                result.shouldFail { coreFailure ->
                    actualFailure = coreFailure
                }
                didContinue = true
            }
        }
        advanceUntilIdle()
        assertFalse(didContinue)
        arrangement.syncStateObserver.mutableSyncState.emit(SyncState.Failed(expectedFailure, 1.seconds))
        advanceUntilIdle()
        waitingJob.join()
        assertTrue(didContinue)
        assertEquals(expectedFailure, actualFailure)
        syncScope.cancel()
    }

    @Test
    fun givenOneRequest_whenSyncScopeIsCancelled_thenShouldStopSyncManagerSubscriptions() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        syncExecutor.request {
            advanceUntilIdle()
            syncScope.cancel()
        }
        syncScope.cancel()
        assertEquals(0, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
        assertEquals(0, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
    }

    @Test
    fun givenSlowSyncCompletes_whenStartingAsNeeded_thenShouldStartIncrementalSync() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        syncExecutor.request {
            arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
            advanceUntilIdle()

            assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
            assertEquals(1, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
        }
        syncScope.cancel()
    }

    @Test
    fun givenSlowSyncCompletes_whenSlowSyncIsReset_thenShouldStopIncrementalSync() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        syncExecutor.request {
            arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
            advanceUntilIdle()

            arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))
            advanceUntilIdle()

            assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
            assertEquals(0, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
        }
        syncScope.cancel()
    }

    @Test
    fun givenNoMoreRequests_whenStartingAsNeeded_thenShouldStopSync() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        syncExecutor.request {
            advanceUntilIdle()
        }
        advanceUntilIdle()
        assertEquals(0, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
        assertEquals(0, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
        syncScope.cancel()
    }

    @Test
    fun givenKeepSyncAlwaysOn_whenRequestBlockEnds_thenShouldKeepSync() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        syncExecutor.request {
            keepSyncAlwaysOn()
            advanceUntilIdle()
        }
        advanceUntilIdle()
        assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
        syncScope.cancel()
    }

    @Test
    fun givenMultipleRequests_whenProcessingSync_thenShouldHaveOnlyOneSubscriptionToEachSyncManager() =
        runTest(TestKaliumDispatcher.default) {
            val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
            val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

            syncExecutor.startAndStopSyncAsNeeded()

            syncExecutor.request {
                advanceUntilIdle()
                syncExecutor.request {
                    advanceUntilIdle()
                    assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
                    assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
                }
            }
            syncScope.cancel()
        }

    private class Arrangement(val syncScope: CoroutineScope) {
        val syncStateObserver = FakeSyncStateObserver()
        val slowSyncManager = FakeSlowSyncManager()
        val incrementalSyncManager = FakeIncrementalSyncManager()

        fun arrange() = this to SyncExecutorImpl(
            syncStateObserver,
            slowSyncManager,
            incrementalSyncManager,
            syncScope
        )
    }

}
