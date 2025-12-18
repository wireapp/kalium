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
package com.wire.kalium.logic.feature.call

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncStateObserver
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CallBackgroundManagerTest {

    fun testProcessingSpecificStates(syncState: SyncState, appInBackground: Boolean, expected: Boolean) = runTest {
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = MutableStateFlow(syncState))
            .arrange(initialBackgroundState = appInBackground)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(expected) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(!expected) }.wasNotInvoked()
            cancel()
        }
    }

    @Test
    fun givenAppInBackground_andSyncStateIsFailed_whenProcessing_thenSetTrue() =
        testProcessingSpecificStates(syncState = SyncStateFailedNoNetwork, appInBackground = true, expected = true)

    @Test
    fun givenAppInBackground_andSyncStateIsWaiting_whenProcessing_thenSetTrue() =
        testProcessingSpecificStates(syncState = SyncState.Waiting, appInBackground = true, expected = true)

    @Test
    fun givenAppInBackground_andSyncStateIsSlowSync_whenProcessing_thenSetFalse() =
        testProcessingSpecificStates(syncState = SyncState.SlowSync, appInBackground = true, expected = false)

    @Test
    fun givenAppInBackground_andSyncStateIsGathering_whenProcessing_thenSetFalse() =
        testProcessingSpecificStates(syncState = SyncState.GatheringPendingEvents, appInBackground = true, expected = false)

    @Test
    fun givenAppInBackground_andSyncStateIsLive_whenProcessing_thenSetFalse() =
        testProcessingSpecificStates(syncState = SyncState.Live, appInBackground = true, expected = false)

    @Test
    fun givenAppInForeground_andSyncStateIsFailed_whenProcessing_thenSetFalse() =
        testProcessingSpecificStates(syncState = SyncStateFailedNoNetwork, appInBackground = false, expected = false)

    @Test
    fun givenAppInForeground_andSyncStateIsWaiting_whenProcessing_thenSetTrue() =
        testProcessingSpecificStates(syncState = SyncState.Waiting, appInBackground = false, expected = false)

    @Test
    fun givenAppInForeground_andSyncStateIsSlowSync_whenProcessing_thenSetFalse() =
        testProcessingSpecificStates(syncState = SyncState.SlowSync, appInBackground = false, expected = false)

    @Test
    fun givenAppInForeground_andSyncStateIsGathering_whenProcessing_thenSetFalse() =
        testProcessingSpecificStates(syncState = SyncState.GatheringPendingEvents, appInBackground = false, expected = false)

    @Test
    fun givenAppInForeground_andSyncStateIsLive_whenProcessing_thenSetFalse() =
        testProcessingSpecificStates(syncState = SyncState.Live, appInBackground = false, expected = false)

    @Test
    fun givenAppChangesFromForegroundToBackground_andSyncIsNotRunning_whenProcessing_thenChangeFromFalseToTrue() = runTest {
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = MutableStateFlow(SyncState.Waiting))
            .arrange(initialBackgroundState = false)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            // Change to background
            callBackgroundManager.setBackground(true)
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 1)

            cancel()
        }
    }

    @Test
    fun givenAppChangesFromForegroundToBackground_andSyncIsRunning_whenProcessing_thenKeepTrue() = runTest {
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = MutableStateFlow(SyncState.GatheringPendingEvents))
            .arrange(initialBackgroundState = false)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            // Change to background
            callBackgroundManager.setBackground(true)
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0) // distinctUntilChanged so no new call
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            cancel()
        }
    }

    @Test
    fun givenAppChangesFromBackgroundToForeground_andSyncIsNotRunning_whenProcessing_thenChangeFromTrueToFalse() = runTest {
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = MutableStateFlow(SyncState.Waiting))
            .arrange(initialBackgroundState = true)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0)

            // Change to background
            callBackgroundManager.setBackground(false)
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)

            cancel()
        }
    }

    @Test
    fun givenAppChangesFromBackgroundToForeground_andSyncIsRunning_whenProcessing_thenKeepTrue() = runTest {
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = MutableStateFlow(SyncState.GatheringPendingEvents))
            .arrange(initialBackgroundState = true)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            // Change to background
            callBackgroundManager.setBackground(false)
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0) // distinctUntilChanged so no new call
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            cancel()
        }
    }

    @Test
    fun givenAppInBackground_andSyncStartsRunning_whenProcessing_thenChangeFromTrueToFalse() = runTest {
        val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Waiting)
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = syncStateFlow)
            .arrange(initialBackgroundState = true)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0)

            // Sync starts running which means connection is up when in background
            syncStateFlow.value = SyncState.GatheringPendingEvents
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)

            cancel()
        }
    }

    @Test
    fun givenAppInBackground_andSyncStopsRunning_whenProcessing_thenChangeFromFalseToTrue() = runTest {
        val syncStateFlow = MutableStateFlow<SyncState>(SyncState.GatheringPendingEvents)
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = syncStateFlow)
            .arrange(initialBackgroundState = true)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            // Sync stops running which means connection is down when in background
            syncStateFlow.value = SyncStateFailedNoNetwork
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 1)

            cancel()
        }
    }

    @Test
    fun givenAppInForeground_andSyncStartsRunning_whenProcessing_thenKeepTrue() = runTest {
        val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Waiting)
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = syncStateFlow)
            .arrange(initialBackgroundState = false)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            // Sync starts running which means connection is up when in foreground
            syncStateFlow.value = SyncState.GatheringPendingEvents
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0) // distinctUntilChanged so no new call
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            cancel()
        }
    }

    @Test
    fun givenAppInForeground_andSyncStopsRunning_whenProcessing_thenKeepTrue() = runTest {
        val syncStateFlow = MutableStateFlow<SyncState>(SyncState.GatheringPendingEvents)
        val (arrangement, callBackgroundManager) = Arrangement()
            .withSyncStateFlow(syncStateFlow = syncStateFlow)
            .arrange(initialBackgroundState = false)

        launch { callBackgroundManager.startProcessing() }.run {
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 1)
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            // Sync stops running which means connection is down when in foreground
            syncStateFlow.value = SyncStateFailedNoNetwork
            advanceUntilIdle()
            coVerify { arrangement.callManager.setBackground(false) }.wasInvoked(exactly = 0) // distinctUntilChanged so no new call
            coVerify { arrangement.callManager.setBackground(true) }.wasInvoked(exactly = 0)

            cancel()
        }
    }

    inner class Arrangement() {
        val callManager = mock(CallManager::class)
        val syncStateObserver: SyncStateObserver = mock(SyncStateObserver::class)

        internal fun withSyncStateFlow(syncStateFlow: StateFlow<SyncState>) = apply {
            every { syncStateObserver.syncState } returns syncStateFlow
        }

        internal fun arrange(initialBackgroundState: Boolean = false) = this to CallBackgroundManagerImpl(
            callManager = lazy { callManager },
            syncStateObserver = lazy { syncStateObserver },
            selfUserId = TestUser.USER_ID,
            initialBackgroundState = initialBackgroundState
        )
    }

    companion object {
        private val SyncStateFailedNoNetwork = SyncState.Failed(NetworkFailure.NoNetworkConnection(null), 1.seconds)
    }
}
