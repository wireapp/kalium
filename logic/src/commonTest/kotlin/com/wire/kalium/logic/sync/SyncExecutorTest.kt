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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
                val result = waitUntilLiveOrFailure()
                assertEquals(SyncRequestResult.Success, result)
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
    fun givenRequestWithWaiter_whenNoOtherRequests_thenShouldStartSync() = runTest(TestKaliumDispatcher.default) {
        val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
        val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

        syncExecutor.startAndStopSyncAsNeeded()

        val waitingJob = backgroundScope.launch {
            syncExecutor.request {
                waitUntilLiveOrFailure()
            }
        }
        advanceUntilIdle()

        assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)

        arrangement.syncStateObserver.mutableSyncState.emit(SyncState.Live)
        waitingJob.join()
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
                val result = waitUntilOrFailure(SyncState.GatheringPendingEvents)
                assertEquals(SyncRequestResult.Success, result)
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
                assertIs<SyncRequestResult.Failure>(result)
                actualFailure = result.error
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
                arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
                advanceUntilIdle()
                syncExecutor.request {
                    advanceUntilIdle()
                    assertEquals(1, arrangement.slowSyncManager.fakeSyncFlow.subscriptionCount.value)
                    assertEquals(1, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
                }
            }
            syncScope.cancel()
        }

    @Test
    fun givenSyncIsConnecting_whenNewRequestStarts_thenShouldNotResetBackoffOrRestartSync() =
        runTest(TestKaliumDispatcher.default) {
            listOf(
                SyncState.Waiting,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
            ).forEach { connectingState ->
                val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
                val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

                syncExecutor.startAndStopSyncAsNeeded()

                syncExecutor.request {
                    arrangement.syncStateObserver.mutableSyncState.emit(connectingState)
                    advanceUntilIdle()

                    syncExecutor.request {
                        advanceUntilIdle()
                        assertEquals(0, arrangement.slowSyncManager.resetRetryBackoffCount)
                        assertEquals(0, arrangement.incrementalSyncManager.resetRetryBackoffCount)
                        assertEquals(1, arrangement.slowSyncManager.performSyncFlowCount)
                        assertEquals(0, arrangement.slowSyncManager.cancelledSyncFlowCount)
                    }
                }
                syncScope.cancel()
            }
        }

    @Test
    fun givenSyncIsLive_whenNewRequestStarts_thenShouldNotRestartOrResetSync() =
        runTest(TestKaliumDispatcher.default) {
            val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
            val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

            syncExecutor.startAndStopSyncAsNeeded()

            syncExecutor.request {
                arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
                advanceUntilIdle()
                assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)

                arrangement.syncStateObserver.mutableSyncState.emit(SyncState.Live)
                advanceUntilIdle()

                syncExecutor.request {
                    advanceUntilIdle()
                    assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)
                    assertEquals(0, arrangement.incrementalSyncManager.cancelledSyncFlowCount)
                    assertEquals(0, arrangement.slowSyncManager.resetRetryBackoffCount)
                    assertEquals(0, arrangement.incrementalSyncManager.resetRetryBackoffCount)
                }
            }
            syncScope.cancel()
        }

    @Test
    fun givenSyncFailed_whenNewRequestStarts_thenShouldRestartSyncImmediately() =
        runTest(TestKaliumDispatcher.default) {
            val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
            val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

            syncExecutor.startAndStopSyncAsNeeded()

            syncExecutor.request {
                arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
                advanceUntilIdle()
                assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)

                arrangement.syncStateObserver.mutableSyncState.emit(
                    SyncState.Failed(CoreFailure.SyncEventOrClientNotFound, 10.seconds)
                )
                advanceUntilIdle()

                syncExecutor.request {
                    advanceUntilIdle()
                    assertEquals(2, arrangement.incrementalSyncManager.performSyncFlowCount)
                    assertEquals(1, arrangement.incrementalSyncManager.cancelledSyncFlowCount)
                    assertEquals(1, arrangement.incrementalSyncManager.fakeSyncFlow.subscriptionCount.value)
                    assertEquals(0, arrangement.slowSyncManager.resetRetryBackoffCount)
                    assertEquals(0, arrangement.incrementalSyncManager.resetRetryBackoffCount)
                }
            }
            syncScope.cancel()
        }

    @Test
    fun givenExistingRequestAndFailedSync_whenWaitingForLive_thenShouldNotRestartSync() =
        runTest(TestKaliumDispatcher.default) {
            val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
            val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

            syncExecutor.startAndStopSyncAsNeeded()

            syncExecutor.request {
                arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
                advanceUntilIdle()
                assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)

                arrangement.syncStateObserver.mutableSyncState.emit(
                    SyncState.Failed(CoreFailure.SyncEventOrClientNotFound, 10.seconds)
                )
                advanceUntilIdle()

                assertIs<SyncRequestResult.Failure>(waitUntilLiveOrFailure())
                advanceUntilIdle()

                assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)
                assertEquals(0, arrangement.incrementalSyncManager.cancelledSyncFlowCount)
            }
            syncScope.cancel()
        }

    @Test
    fun givenFailedSync_whenNewRequestWaitsForLive_thenShouldRestartSyncExactlyOnce() =
        runTest(TestKaliumDispatcher.default) {
            val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
            val (arrangement, syncExecutor) = Arrangement(syncScope).arrange()

            syncExecutor.startAndStopSyncAsNeeded()

            syncExecutor.request {
                arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
                advanceUntilIdle()
                assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)

                arrangement.syncStateObserver.mutableSyncState.emit(
                    SyncState.Failed(CoreFailure.SyncEventOrClientNotFound, 10.seconds)
                )
                advanceUntilIdle()

                val result = syncExecutor.request {
                    waitUntilLiveOrFailure()
                }
                advanceUntilIdle()

                assertIs<SyncRequestResult.Failure>(result)
                assertEquals(2, arrangement.incrementalSyncManager.performSyncFlowCount)
                assertEquals(1, arrangement.incrementalSyncManager.cancelledSyncFlowCount)
            }
            syncScope.cancel()
        }

    @Test
    fun givenFailedStateNotYetDeliveredToCollectors_whenNewRequestStarts_thenShouldRestartSyncImmediately() =
        runTest(TestKaliumDispatcher.default) {
            val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
            val syncStateObserver = PausableSyncStateObserver()
            val (arrangement, syncExecutor) = Arrangement(syncScope, syncStateObserver).arrange()

            syncExecutor.startAndStopSyncAsNeeded()

            syncExecutor.request {
                arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
                syncStateObserver.mutableSyncState.value = SyncState.Live
                advanceUntilIdle()
                assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)

                syncStateObserver.mutableSyncState.pauseDelivery()
                syncStateObserver.mutableSyncState.value =
                    SyncState.Failed(CoreFailure.SyncEventOrClientNotFound, 10.seconds)

                syncExecutor.request {
                    advanceUntilIdle()
                    assertEquals(2, arrangement.incrementalSyncManager.performSyncFlowCount)
                    assertEquals(1, arrangement.incrementalSyncManager.cancelledSyncFlowCount)
                }
            }
            syncScope.cancel()
        }

    @Test
    fun givenRecoveredStateNotYetDeliveredToCollectors_whenNewRequestStarts_thenShouldNotRestartSync() =
        runTest(TestKaliumDispatcher.default) {
            val syncScope = CoroutineScope(coroutineContext + SupervisorJob())
            val syncStateObserver = PausableSyncStateObserver()
            val (arrangement, syncExecutor) = Arrangement(syncScope, syncStateObserver).arrange()

            syncExecutor.startAndStopSyncAsNeeded()

            syncExecutor.request {
                arrangement.slowSyncManager.fakeSyncFlow.emit(SlowSyncStatus.Complete)
                syncStateObserver.mutableSyncState.value =
                    SyncState.Failed(CoreFailure.SyncEventOrClientNotFound, 10.seconds)
                advanceUntilIdle()
                assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)

                syncStateObserver.mutableSyncState.pauseDelivery()
                syncStateObserver.mutableSyncState.value = SyncState.Live

                syncExecutor.request {
                    advanceUntilIdle()
                    assertEquals(1, arrangement.incrementalSyncManager.performSyncFlowCount)
                    assertEquals(0, arrangement.incrementalSyncManager.cancelledSyncFlowCount)
                }
            }
            syncScope.cancel()
        }

    private class Arrangement(
        val syncScope: CoroutineScope,
        syncStateObserverOverride: SyncStateObserver? = null,
    ) {
        val syncStateObserver = FakeSyncStateObserver()
        val slowSyncManager = FakeSlowSyncManager()
        val incrementalSyncManager = FakeIncrementalSyncManager()
        private val observedSyncState = syncStateObserverOverride ?: syncStateObserver

        fun arrange() = this to SyncExecutorImpl(
            observedSyncState,
            slowSyncManager,
            incrementalSyncManager,
            syncScope
        )
    }

    private class PausableSyncStateObserver(
        delegate: FakeSyncStateObserver = FakeSyncStateObserver(),
    ) : SyncStateObserver by delegate {
        val mutableSyncState = PausableStateFlow<SyncState>(SyncState.Waiting)
        override val syncState: StateFlow<SyncState> = mutableSyncState
    }

    private class PausableStateFlow<T>(initialValue: T) : StateFlow<T> {
        private val delegate = MutableStateFlow(initialValue)
        private val deliveryEnabled = MutableStateFlow(true)

        override var value: T
            get() = delegate.value
            set(value) {
                delegate.value = value
            }

        override val replayCache: List<T>
            get() = delegate.replayCache

        fun pauseDelivery() {
            deliveryEnabled.value = false
        }

        override suspend fun collect(collector: FlowCollector<T>): Nothing = delegate.collect { value ->
            deliveryEnabled.first { it }
            collector.emit(value)
        }
    }

}
