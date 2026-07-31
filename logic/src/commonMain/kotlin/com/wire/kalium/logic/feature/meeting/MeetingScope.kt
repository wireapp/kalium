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

import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.util.KaliumDispatcher

public class MeetingScope internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val meetingRepository: MeetingRepository,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    private val transactionProvider: CryptoTransactionProvider,
) {
    public val getPaginatedMeetingOccurrenceDetails: GetPaginatedMeetingOccurrencesUseCase
        get() = GetPaginatedMeetingOccurrencesUseCaseImpl(
            dispatcher = dispatcher,
            meetingRepository = meetingRepository,
        )

    public val observeMeetingOccurrence: ObserveMeetingOccurrenceUseCase
        get() = ObserveMeetingOccurrenceUseCaseImpl(
            dispatcher = dispatcher,
            meetingRepository = meetingRepository,
        )

    public val deleteMeeting: DeleteMeetingUseCase
        get() = DeleteMeetingUseCaseImpl(
            meetingRepository = meetingRepository,
        )

    public val createNewMeeting: CreateNewMeetingUseCase
        get() = CreateNewMeetingUseCaseImpl(
            meetingRepository = meetingRepository,
            refreshUsersWithoutMetadata = refreshUsersWithoutMetadata,
            transactionProvider = transactionProvider,
        )
}
