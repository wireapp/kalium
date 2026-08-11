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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.MeetingRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteMeetingUseCaseTest {

    @Test
    fun givenRepositoryDeleteSucceeds_whenInvoking_thenReturnsSuccess() = runTest {
        val meetingId = MeetingId("meetingId", "domain")
        val (arrangement, useCase) = Arrangement()
            .withDeleteMeetingReturning(meetingId, Either.Right(Unit))
            .arrange()

        val result = useCase(meetingId)

        assertEquals(DeleteMeetingUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.deleteMeeting(meetingId)
        }
    }

    @Test
    fun givenRepositoryDeleteFails_whenInvoking_thenReturnsFailure() = runTest {
        val meetingId = MeetingId("meetingId", "domain")
        val failure = CoreFailure.Unknown(RuntimeException("delete failed"))
        val (_, useCase) = Arrangement()
            .withDeleteMeetingReturning(meetingId, Either.Left(failure))
            .arrange()

        val result = useCase(meetingId)

        assertEquals(failure, assertIs<DeleteMeetingUseCase.Result.Failure>(result).coreFailure)
    }

    inner class Arrangement {
        internal val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)

        internal fun withDeleteMeetingReturning(meetingId: MeetingId, result: Either<CoreFailure, Unit>) = apply {
            everySuspend { meetingRepository.deleteMeeting(meetingId) } returns result
        }

        internal fun arrange() = this to DeleteMeetingUseCaseImpl(meetingRepository)
    }
}
