/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.team

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first

internal interface SyncSelfTeamUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncSelfTeamUseCaseImpl(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) : SyncSelfTeamUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> {
        val user = userRepository.observeSelfUser().first()

        return user.teamId?.let { teamId ->
            teamRepository.fetchTeamById(teamId = teamId).flatMap {
                teamRepository.fetchMembersByTeamId(
                    teamId = teamId,
                    userDomain = user.id.domain
                )
            }.onSuccess {
                teamRepository.syncServices(teamId = teamId)
            }
        } ?: run {
            kaliumLogger.withFeatureId(SYNC).i("Skipping team sync because user doesn't belong to a team")
            Either.Right(Unit)
        }
    }
}
