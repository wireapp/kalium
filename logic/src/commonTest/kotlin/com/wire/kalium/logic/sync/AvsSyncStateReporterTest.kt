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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AvsSyncStateReporterTest {

    @Test
    fun givenGatheringEventsSyncState_whenStartingReporting_thenInvokeReportProcessNotificationsWithTrue() =
        runTest {
            val (_, avsSyncStateReporter) = Arrangement()
                .withGatheringEventsIncrementalSyncState()
                .arrange()

            avsSyncStateReporter.execute()

            verify(avsSyncStateReporter.callManager.value)
                .suspendFunction(avsSyncStateReporter.callManager.value::reportProcessNotifications)
                .with(eq(true))
                .wasInvoked(once)
        }

    @Test
    fun givenLiveSyncState_whenStartingReporting_thenInvokeReportProcessNotificationsWithFalse() =
        runTest {
            val (_, avsSyncStateReporter) = Arrangement()
                .withLiveIncrementalSyncState()
                .arrange()

            avsSyncStateReporter.execute()

            verify(avsSyncStateReporter.callManager.value)
                .suspendFunction(avsSyncStateReporter.callManager.value::reportProcessNotifications)
                .with(eq(false))
                .wasInvoked(once)
        }

    @Test
    fun givenPendingIncrementalSyncState_whenStartingReporting_thenInvokeReportProcessNotificationsWithFalse() =
        runTest {
            val (_, avsSyncStateReporter) = Arrangement()
                .withPendingIncrementalSyncState()
                .arrange()

            avsSyncStateReporter.execute()

            verify(avsSyncStateReporter.callManager.value)
                .suspendFunction(avsSyncStateReporter.callManager.value::reportProcessNotifications)
                .with(eq(false))
                .wasInvoked()
        }

    @Test
    fun givenFailedIncrementalSyncState_whenStartingReporting_thenInvokeReportProcessNotificationsWithFalse() =
        runTest {
            val (_, avsSyncStateReporter) = Arrangement()
                .withFailedIncrementalSyncState()
                .arrange()

            avsSyncStateReporter.execute()

            verify(avsSyncStateReporter.callManager.value)
                .suspendFunction(avsSyncStateReporter.callManager.value::reportProcessNotifications)
                .with(eq(false))
                .wasInvoked()
        }

    private class Arrangement {

        @Mock
        val callManager: CallManager = mock(classOf<CallManager>())

        @Mock
        val incrementalSyncRepository: IncrementalSyncRepository =
            mock(classOf<IncrementalSyncRepository>())

        fun arrange() = this to AvsSyncStateReporterImpl(
            callManager = lazy { callManager },
            incrementalSyncRepository = incrementalSyncRepository,
            kaliumLogger = kaliumLogger
        )

        fun withGatheringEventsIncrementalSyncState() = apply {
            given(incrementalSyncRepository)
                .invocation { incrementalSyncRepository.incrementalSyncState }
                .thenReturn(flowOf(IncrementalSyncStatus.FetchingPendingEvents))
        }

        fun withLiveIncrementalSyncState() = apply {
            given(incrementalSyncRepository)
                .invocation { incrementalSyncRepository.incrementalSyncState }
                .thenReturn(flowOf(IncrementalSyncStatus.Live))
        }

        fun withPendingIncrementalSyncState() = apply {
            given(incrementalSyncRepository)
                .invocation { incrementalSyncRepository.incrementalSyncState }
                .thenReturn(flowOf(IncrementalSyncStatus.Pending))
        }

        fun withFailedIncrementalSyncState() = apply {
            given(incrementalSyncRepository)
                .invocation { incrementalSyncRepository.incrementalSyncState }
                .thenReturn(flowOf(IncrementalSyncStatus.Failed(CoreFailure.SyncEventOrClientNotFound)))
        }
    }
}
