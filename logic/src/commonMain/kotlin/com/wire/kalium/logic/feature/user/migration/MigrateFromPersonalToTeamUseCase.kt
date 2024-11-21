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
package com.wire.kalium.logic.feature.user.migration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

/**
 * Use case to migrate user personal account to team account.
 * This needs at least API V7 to work.
 */
interface MigrateFromPersonalToTeamUseCase {
    suspend operator fun invoke(teamName: String): MigrateFromPersonalToTeamResult
}

sealed class MigrateFromPersonalToTeamResult {
    data object Success : MigrateFromPersonalToTeamResult()
    data class Error(val failure: CoreFailure) : MigrateFromPersonalToTeamResult()
}

internal class MigrateFromPersonalToTeamUseCaseImpl internal constructor(
    private val selfUserId: UserId,
    private val userRepository: UserRepository,
    private val invalidateTeamId: () -> Unit
) : MigrateFromPersonalToTeamUseCase {
    override suspend operator fun invoke(
        teamName: String,
    ): MigrateFromPersonalToTeamResult {
        return userRepository.migrateUserToTeam(teamName)
            .fold(
                { error -> return MigrateFromPersonalToTeamResult.Error(error) },
                { user ->
                    userRepository.updateTeamId(selfUserId, TeamId(user.teamId))
                    invalidateTeamId()
                    MigrateFromPersonalToTeamResult.Success
                }
            )
    }
}
