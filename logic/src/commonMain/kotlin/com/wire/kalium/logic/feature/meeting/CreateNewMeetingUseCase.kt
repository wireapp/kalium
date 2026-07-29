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

package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.meeting.CreateMeeting
import com.wire.kalium.logic.data.meeting.MeetingRepository

/**
 * Use case for creating a new meeting.
 */
public interface CreateNewMeetingUseCase {
    public suspend operator fun invoke(createMeeting: CreateMeeting): Result
    public sealed interface Result {
        public data object Success : Result
        public data object Failure : Result // TODO: Add more specific error types in the future
    }
}

internal class CreateNewMeetingUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val transactionProvider: CryptoTransactionProvider,
) : CreateNewMeetingUseCase {

    override suspend operator fun invoke(createMeeting: CreateMeeting) =
        transactionProvider.transaction("CreateNewMeeting") { transactionContext ->
            meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = transactionContext)
        }.fold({ CreateNewMeetingUseCase.Result.Failure }, { CreateNewMeetingUseCase.Result.Success })
}
