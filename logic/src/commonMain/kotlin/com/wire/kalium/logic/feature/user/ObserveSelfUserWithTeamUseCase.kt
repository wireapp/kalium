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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for retrieving the current user and his/her team.
 */
interface ObserveSelfUserWithTeamUseCase {

    /**
     * @return a [Flow] of Pair, where [Pair.first] is the current user [SelfUser] and [Pair.second] is the Team of the current User [Team]
     */
    suspend operator fun invoke(): Flow<Pair<SelfUser, Team?>>

}

internal class ObserveSelfUserWithTeamUseCaseImpl internal constructor(private val userRepository: UserRepository) :
    ObserveSelfUserWithTeamUseCase {

    override suspend operator fun invoke(): Flow<Pair<SelfUser, Team?>> {
        return userRepository.observeSelfUserWithTeam()
    }
}
