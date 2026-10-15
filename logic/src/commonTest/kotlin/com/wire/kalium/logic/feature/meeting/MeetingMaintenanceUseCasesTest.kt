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
package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingMaintenanceUseCasesTest {
    @Test
    fun localMaintenancePropagatesStorageFailure() = runTest {
        val repository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        everySuspend { repository.syncMeetingOccurrences() } returns Either.Left(StorageFailure.DataNotFound)
        val useCase = MaintainMeetingOccurrencesUseCaseImpl(
            StandardTestDispatcher(testScheduler).testKaliumDispatcher(), repository,
        )
        assertEquals(MaintainMeetingOccurrencesUseCase.Result.Failure(StorageFailure.DataNotFound), useCase())
        verifySuspend(VerifyMode.exactly(1)) { repository.syncMeetingOccurrences() }
    }

    @Test
    fun localMaintenanceReportsSuccess() = runTest {
        val repository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        everySuspend { repository.syncMeetingOccurrences() } returns Either.Right(Unit)
        val useCase = MaintainMeetingOccurrencesUseCaseImpl(
            StandardTestDispatcher(testScheduler).testKaliumDispatcher(), repository,
        )
        assertEquals(MaintainMeetingOccurrencesUseCase.Result.Success, useCase())
    }

}
