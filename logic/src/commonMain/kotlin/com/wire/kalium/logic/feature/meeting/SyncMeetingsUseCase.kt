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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCase

/** Synchronizes the current user's meetings from the backend when meetings are enabled. */
public interface SyncMeetingsUseCase {
    public suspend fun isEnabled(): Boolean
    public suspend operator fun invoke(): Result
    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val coreFailure: CoreFailure) : Result
    }
}

/**
 * This use case will sync against the backend the meetings of the current user.
 */
internal class SyncMeetingsUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val isMeetingsEnabledUseCase: IsMeetingsEnabledUseCase,
    private val transactionProvider: CryptoTransactionProvider
) : SyncMeetingsUseCase {

    override suspend fun isEnabled(): Boolean = isMeetingsEnabledUseCase.invoke()

    override suspend operator fun invoke(): SyncMeetingsUseCase.Result = when (isEnabled()) {
        false -> SyncMeetingsUseCase.Result.Success
        true -> transactionProvider.transaction("SyncMeetings") {
            meetingRepository.fetchAndPersistMeetings(it)
        }.fold(
            { failure ->
                when (failure) {
                    is NetworkFailure.FeatureNotSupported -> SyncMeetingsUseCase.Result.Success
                    else -> SyncMeetingsUseCase.Result.Failure(failure)
                }
            },
            { SyncMeetingsUseCase.Result.Success }
        )
    }
}

internal fun SyncMeetingsUseCase.Result.asEither() = when (this) {
    is SyncMeetingsUseCase.Result.Success -> Either.Right(Unit)
    is SyncMeetingsUseCase.Result.Failure -> Either.Left(coreFailure)
}
