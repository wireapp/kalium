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

import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * This use case is responsible for getting the team of the self user.
 * fixme: this can be replaced, since we are not using the team, but the team id, we can inject the team id directly
 * @see [SelfTeamIdProvider]
 */
fun interface GetSelfTeamUseCase {
    suspend operator fun invoke(): Flow<Team?>
}

@OptIn(ExperimentalCoroutinesApi::class)
class GetSelfTeamUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
) : GetSelfTeamUseCase {
    override suspend operator fun invoke(): Flow<Team?> {
        return userRepository.observeSelfUser()
            .flatMapLatest {
                if (it.teamId != null) teamRepository.getTeam(it.teamId)
                else flow { emit(it.teamId) }
            }
    }
}
