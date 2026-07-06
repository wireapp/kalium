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
package com.wire.kalium.logic.sync.periodic

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.sync.Result
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingOccurrencesSyncWorkerTest {

    @Test
    fun givenRepositorySyncSucceeds_whenDoingWork_thenSuccessIsReturned() = runTest {
        val (_, worker) = Arrangement()
            .withSyncMeetingOccurrencesSuccess()
            .arrange()
        val result = worker.doWork()
        assertEquals(Result.Success, result)
    }

    @Test
    fun givenRepositorySyncFails_whenDoingWork_thenFailureIsReturned() = runTest {
        val (_, worker) = Arrangement()
            .withSyncMeetingOccurrencesFailure()
            .arrange()
        val result = worker.doWork()
        assertEquals(Result.Failure, result)
    }

    inner class Arrangement {
        internal val meetingRepository = mock<MeetingRepository>()

        fun withSyncMeetingOccurrencesSuccess() = apply {
            everySuspend { meetingRepository.syncMeetingOccurrences() } returns Unit.right()
        }
        fun withSyncMeetingOccurrencesFailure() = apply {
            everySuspend { meetingRepository.syncMeetingOccurrences() } returns CoreFailure.Unknown(RuntimeException("sync failed")).left()
        }

        internal fun arrange() = this to MeetingOccurrencesSyncWorkerImpl(meetingRepository)

    }
}
