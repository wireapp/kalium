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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport

internal interface SyncMeetingsUseCase {
    fun isEnabled(): Boolean
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

/**
 * This use case will sync against the backend the meetings of the current user.
 */
internal class SyncMeetingsUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val userRepository: UserRepository,
    private val featureSupport: FeatureSupport,
    private val transactionProvider: CryptoTransactionProvider
) : SyncMeetingsUseCase {

    override fun isEnabled(): Boolean = featureSupport.isMeetingsSupported

    override suspend operator fun invoke(): Either<CoreFailure, Unit> = when (isEnabled()) {
        false -> Either.Right(Unit)
        true -> transactionProvider.transaction("SyncMeetings") {
            meetingRepository.fetchAndPersistMeetings()
                .flatMap { meetings ->
                    val creatorIds = meetings.map { it.creatorId.toModel() }.toSet()
                    if (creatorIds.isEmpty()) {
                        Either.Right(Unit)
                    } else {
                        userRepository.fetchUsersIfUnknownByIds(creatorIds)
                    }
                }
        }.flatMapLeft {
            when (it) {
                is NetworkFailure.FeatureNotSupported -> Either.Right(Unit)
                else -> Either.Left(it)
            }
        }
    }
}
