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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.wrapStorageRequest
import kotlinx.coroutines.flow.firstOrNull

/**
 * Use case that allows getting the user details of a user, either locally or externally
 */
fun interface GetUserInfoUseCase {
    /**
     * Use case [GetUserInfoUseCase] operation
     *
     * @param userId the target user identifier
     * @return a [GetUserInfoResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): GetUserInfoResult
}

internal class GetUserInfoUseCaseImpl(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) : GetUserInfoUseCase {

    override suspend fun invoke(userId: UserId): GetUserInfoResult {
        return getOtherUser(userId).fold(
            { GetUserInfoResult.Failure },
            { otherUser ->
                getOtherUserTeam(otherUser).fold(
                    { GetUserInfoResult.Failure },
                    { team -> GetUserInfoResult.Success(otherUser, team) })
            }
        )
    }

    private suspend fun getOtherUser(userId: UserId): Either<CoreFailure, OtherUser> {
        return wrapStorageRequest {
            val localOtherUser = userRepository.getKnownUser(userId).firstOrNull()

            return if (localOtherUser != null) {
                Either.Right(localOtherUser)
            } else {
                userRepository.userById(userId)
            }
        }
    }

    /**
     * Users are allowed to fetch team details only if they are members of the same team
     * @see [UserType]
     */
    private suspend fun getOtherUserTeam(otherUser: OtherUser): Either<CoreFailure, Team?> {
        val teamId = otherUser.teamId
        return if (teamId != null && otherUser.userType in listOf(UserType.INTERNAL, UserType.OWNER)) {
            val localTeam = teamRepository.getTeam(teamId).firstOrNull()

            if (localTeam == null) {
                teamRepository.fetchTeamById(teamId)
            } else {
                Either.Right(localTeam)
            }
        } else {
            Either.Right(null)
        }
    }

}

sealed class GetUserInfoResult {
    class Success(val otherUser: OtherUser, val team: Team?) : GetUserInfoResult()
    data object Failure : GetUserInfoResult()
}
