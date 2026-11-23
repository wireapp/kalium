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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvitationLinkFeatureUnavailable
import com.wire.kalium.network.exceptions.isMeetingNotFound
import com.wire.kalium.network.exceptions.isWrongMeetingPassword

/** Joins a meeting guest link and persists its meeting and conversation for the meeting list. */
public interface JoinMeetingViaCodeUseCase {
    public suspend operator fun invoke(domain: String, key: String, code: String, password: String? = null): Result

    public sealed interface Result {
        public data object Success : Result
        public data object InvalidPassword : Result
        public data object MeetingNotFound : Result
        public data object LinkUnavailable : Result
        public data object Failure : Result
    }
}

internal class JoinMeetingViaCodeUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val transactionProvider: CryptoTransactionProvider,
) : JoinMeetingViaCodeUseCase {
    override suspend fun invoke(domain: String, key: String, code: String, password: String?): JoinMeetingViaCodeUseCase.Result =
        transactionProvider.transaction("JoinMeetingViaCode") { context ->
            meetingRepository.joinViaCode(domain, key, code, password, context)
        }.fold(
            { failure ->
                val exception = (failure as? NetworkFailure.ServerMiscommunication)?.kaliumException
                val error = exception as? KaliumException.InvalidRequestError
                when {
                    error?.isMeetingNotFound() == true -> JoinMeetingViaCodeUseCase.Result.MeetingNotFound
                    error?.isWrongMeetingPassword() == true -> JoinMeetingViaCodeUseCase.Result.InvalidPassword
                    error?.isInvitationLinkFeatureUnavailable() == true -> JoinMeetingViaCodeUseCase.Result.LinkUnavailable
                    else -> JoinMeetingViaCodeUseCase.Result.Failure
                }
            },
            { JoinMeetingViaCodeUseCase.Result.Success }
        )
}
