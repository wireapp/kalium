/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RotateMeetingLinkUseCaseTest {
    @Test
    fun givenRotationSucceeds_whenInvoked_thenReturnsMeetingAndForwardsOptionalPassword() = runTest {
        val repository = mock<MeetingRepository>()
        val useCase = RotateMeetingLinkUseCaseImpl(repository)
        for (password in listOf(null, "Secret123!")) {
            everySuspend { repository.rotateMeetingLink(MEETING.meetingId, password) } returns Either.Right(MEETING)

            assertEquals(RotateMeetingLinkUseCase.Result.Success(MEETING), useCase(MEETING.meetingId, password))
            verifySuspend(VerifyMode.exactly(1)) { repository.rotateMeetingLink(MEETING.meetingId, password) }
        }
    }

    @Test
    fun givenRotationFails_whenInvoked_thenReturnsOriginalFailure() = runTest {
        val repository = mock<MeetingRepository>()
        everySuspend { repository.rotateMeetingLink(MEETING.meetingId, null) } returns Either.Left(StorageFailure.DataNotFound)

        assertEquals(
            RotateMeetingLinkUseCase.Result.Failure(StorageFailure.DataNotFound),
            RotateMeetingLinkUseCaseImpl(repository)(MEETING.meetingId),
        )
    }

    private companion object {
        val MEETING = Meeting(
            meetingId = MeetingId("meeting", "wire.test"),
            conversationId = ConversationId("conversation", "wire.test"),
            creatorId = UserId("creator", "wire.test"),
            title = "Meeting",
            startTime = Instant.parse("2026-11-16T10:00:00Z"),
            endTime = Instant.parse("2026-11-16T11:00:00Z"),
            tzid = "Europe/Warsaw",
            recurrence = null,
            link = "https://wire.test/new-link",
            hasPassword = true,
        )
    }
}
