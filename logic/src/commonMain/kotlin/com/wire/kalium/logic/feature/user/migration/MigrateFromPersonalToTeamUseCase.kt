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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException

/**
 * Use case to migrate user personal account to team account.
 * This needs at least API V7 to work.
 */
interface MigrateFromPersonalToTeamUseCase {
    suspend operator fun invoke(teamName: String): MigrateFromPersonalToTeamResult
}

sealed class MigrateFromPersonalToTeamResult {
    data class Success(val teamName: String) : MigrateFromPersonalToTeamResult()
    data class Error(val failure: MigrateFromPersonalToTeamFailure) : MigrateFromPersonalToTeamResult()
}

sealed class MigrateFromPersonalToTeamFailure {

    data class UnknownError(val coreFailure: CoreFailure) : MigrateFromPersonalToTeamFailure()
    class UserAlreadyInTeam : MigrateFromPersonalToTeamFailure() {
        companion object {
            const val ERROR_LABEL = "user-already-in-a-team"
        }
    }
    data object NoNetwork : MigrateFromPersonalToTeamFailure()
}

internal class MigrateFromPersonalToTeamUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
) : MigrateFromPersonalToTeamUseCase {
    override suspend operator fun invoke(
        teamName: String,
    ): MigrateFromPersonalToTeamResult {
        return userRepository.migrateUserToTeam(teamName)
            .fold(
                { error ->
                    if (error is NetworkFailure.ServerMiscommunication && error.kaliumException is KaliumException.InvalidRequestError) {
                        val response = error.kaliumException.errorResponse
                        if(response.label == MigrateFromPersonalToTeamFailure.UserAlreadyInTeam.ERROR_LABEL) {
                            return MigrateFromPersonalToTeamResult.Error(MigrateFromPersonalToTeamFailure.UserAlreadyInTeam())
                        }
                    }
                    if(error is NetworkFailure.NoNetworkConnection) {
                        return MigrateFromPersonalToTeamResult.Error(MigrateFromPersonalToTeamFailure.NoNetwork)
                    }
                    return MigrateFromPersonalToTeamResult.Error(MigrateFromPersonalToTeamFailure.UnknownError(error))
                },
                { success ->
                    // TODO Invalidate team id in memory so UserSessionScope.selfTeamId got updated data WPB-12187
                    MigrateFromPersonalToTeamResult.Success(teamName = success.teamName)
                }
            )
    }
}
