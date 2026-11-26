/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository

/**
 * Revokes the previous guest link and persists the updated link and password protection status.
 * Requires API v19. A null password creates a link without password protection.
 */
public interface RotateMeetingLinkUseCase {
    public suspend operator fun invoke(meetingId: MeetingId, password: String? = null): Result

    public sealed interface Result {
        public data class Success(val meeting: Meeting) : Result
        public data class Failure(val coreFailure: CoreFailure) : Result
    }
}

internal class RotateMeetingLinkUseCaseImpl(
    private val meetingRepository: MeetingRepository,
) : RotateMeetingLinkUseCase {
    override suspend fun invoke(meetingId: MeetingId, password: String?): RotateMeetingLinkUseCase.Result =
        meetingRepository.rotateMeetingLink(meetingId, password).fold(
            { RotateMeetingLinkUseCase.Result.Failure(it) },
            { RotateMeetingLinkUseCase.Result.Success(it) },
        )
}
