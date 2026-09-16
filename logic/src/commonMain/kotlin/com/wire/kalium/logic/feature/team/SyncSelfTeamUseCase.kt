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

package com.wire.kalium.logic.feature.team

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal interface SyncSelfTeamUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncSelfTeamUseCaseImpl(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val fetchedUsersLimit: Int?
) : SyncSelfTeamUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> =
        userRepository.getSelfUser().flatMap { selfUser ->
            selfUser.teamId?.let { teamId ->
                teamRepository.fetchTeamById(teamId = teamId).flatMap {
                    coroutineScope {
                        launch {
                            teamRepository.fetchMembersByTeamId(
                                teamId = teamId,
                                userDomain = selfUser.id.domain,
                                fetchedUsersLimit = fetchedUsersLimit
                            ).onFailure { kaliumLogger.withFeatureId(SYNC).w("Failed to fetch team members: $it") }
                        }
                        launch {
                            teamRepository.fetchAppsByTeamId(teamId = teamId)
                                .onFailure { kaliumLogger.withFeatureId(SYNC).w("Failed to fetch team apps: $it") }
                        }
                        launch {
                            teamRepository.fetchCollaboratorsByTeamId(teamId = teamId)
                                .onFailure { kaliumLogger.withFeatureId(SYNC).w("Failed to fetch team collaborators: $it") }
                        }
                        launch {
                            teamRepository.syncServices(teamId = teamId)
                                .onFailure { kaliumLogger.withFeatureId(SYNC).w("Failed to sync team services: $it") }
                        }
                    }
                    Either.Right(Unit)
                }
            } ?: run {
                kaliumLogger.withFeatureId(SYNC).i("Skipping team sync because user doesn't belong to a team")
                Either.Right(Unit)
            }
        }
}
