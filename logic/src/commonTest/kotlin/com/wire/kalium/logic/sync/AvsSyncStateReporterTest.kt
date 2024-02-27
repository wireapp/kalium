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

import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AvsSyncStateReporterTest {

    @Test
    fun givenGatheringEventsSyncState_whenStartingReporting_thenInvokeReportProcessNotificationsWithTrue() = runTest {
        val (_, avsSyncStateReporter) = Arrangement()
            .withGatheringEventsSyncState()
            .arrange()

        avsSyncStateReporter.execute()

        verify(avsSyncStateReporter.callManager.value)
            .suspendFunction(avsSyncStateReporter.callManager.value::reportProcessNotifications)
            .with(eq(true))
            .wasInvoked(once)
    }

    @Test
    fun givenLiveSyncState_whenStartingReporting_thenInvokeReportProcessNotificationsWithFalse() = runTest {
        val (_, avsSyncStateReporter) = Arrangement()
            .withLiveSyncState()
            .arrange()

        avsSyncStateReporter.execute()

        verify(avsSyncStateReporter.callManager.value)
            .suspendFunction(avsSyncStateReporter.callManager.value::reportProcessNotifications)
            .with(eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenOtherSyncState_whenStartingReporting_thenDoNotInvokeReportProcessNotifications() = runTest {
        val (_, avsSyncStateReporter) = Arrangement()
            .withOtherSyncState()
            .arrange()

        avsSyncStateReporter.execute()

        verify(avsSyncStateReporter.callManager.value)
            .suspendFunction(avsSyncStateReporter.callManager.value::reportProcessNotifications)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val observeSyncStateUseCase: ObserveSyncStateUseCase = mock(classOf<ObserveSyncStateUseCase>())

        @Mock
        val callManager: CallManager = mock(classOf<CallManager>())

        fun arrange() = this to AvsSyncStateReporterImpl(
            callManager = lazy { callManager },
            observeSyncStateUseCase = observeSyncStateUseCase,
            kaliumLogger = kaliumLogger
        )

        fun withGatheringEventsSyncState() = apply {
            given(observeSyncStateUseCase)
                .function(observeSyncStateUseCase::invoke)
                .whenInvoked()
                .thenReturn(flow { emit(SyncState.GatheringPendingEvents) })
        }

        fun withLiveSyncState() = apply {
            given(observeSyncStateUseCase)
                .function(observeSyncStateUseCase::invoke)
                .whenInvoked()
                .thenReturn(flow { emit(SyncState.Live) })
        }

        fun withOtherSyncState() = apply {
            given(observeSyncStateUseCase)
                .function(observeSyncStateUseCase::invoke)
                .whenInvoked()
                .thenReturn(flow { emit(SyncState.SlowSync) })
        }
    }
}
