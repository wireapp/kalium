/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class ObserveSyncStateUseCaseTest {

    @Test
    fun givenObserverFlowEmitsValues_whenInvoking_thenShouldForwardTheSameValues() = runTest(TestKaliumDispatcher.default) {
        val expectedValues = listOf(
            SyncState.Waiting,
            SyncState.GatheringPendingEvents,
            SyncState.Live,
            SyncState.SlowSync,
            SyncState.Failed(CoreFailure.SyncEventOrClientNotFound, Duration.ZERO)
        ).iterator()
        var currentExpectedValue = expectedValues.next()
        val statesFlow = MutableStateFlow<SyncState>(currentExpectedValue)

        val (_, useCase) = Arrangement()
            .withSyncStates(statesFlow)
            .arrange()

        useCase().test {
            // Item 0
            val item = awaitItem()
            assertEquals(currentExpectedValue, item)

            // Remaining items
            while(expectedValues.hasNext()) {
                currentExpectedValue = expectedValues.next()
                statesFlow.emit(currentExpectedValue)
                val item = awaitItem()
                assertEquals(currentExpectedValue, item)
            }

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {

        @Mock
        val syncStateObserver: SyncStateObserver = mock(SyncStateObserver::class)

        fun withSyncStates(flow: StateFlow<SyncState>) = apply {
            every {
                syncStateObserver.syncState
            }.returns(flow)
        }

        fun arrange() = this to ObserveSyncStateUseCaseImpl(
            syncStateObserver = syncStateObserver
        )

    }
}
