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
package com.wire.kalium.logic.feature.team.migration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold

interface MigrateFromPersonalToTeamUseCase {
    suspend operator fun invoke(teamName: String): MigrateFromPersonalToTeamResult
}

sealed class MigrateFromPersonalToTeamResult {
    data class Success(val teamId: String, val teamName: String) : MigrateFromPersonalToTeamResult()
    data class Error(val failure: CoreFailure) : MigrateFromPersonalToTeamResult()
}

internal class MigrateFromPersonalToTeamUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
) : MigrateFromPersonalToTeamUseCase {
    override suspend operator fun invoke(
        teamName: String,
    ): MigrateFromPersonalToTeamResult {
        return userRepository.migrateUserToTeam(teamName)
            .fold(
                { error -> return MigrateFromPersonalToTeamResult.Error(error) },
                { success ->
                    MigrateFromPersonalToTeamResult.Success(
                        teamId = success.teamId,
                        teamName = success.teamName,
                    )
                })
    }
}