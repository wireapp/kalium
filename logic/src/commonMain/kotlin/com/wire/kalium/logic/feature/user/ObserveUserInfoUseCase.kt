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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapRightWithEither
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.common.functional.mapToRightOr
import com.wire.kalium.logic.wrapStorageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Use case that allows observing the user details of a user locally,
 * or request it from API and save to DB, if there is no local data for such user.
 */
interface ObserveUserInfoUseCase {
    /**
     * Use case [GetUserInfoUseCase] operation
     *
     * @param userId the target user identifier
     * @return a [GetUserInfoResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): Flow<GetUserInfoResult>
}

internal class ObserveUserInfoUseCaseImpl(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) : ObserveUserInfoUseCase {

    override suspend fun invoke(userId: UserId): Flow<GetUserInfoResult> {
        return observeOtherUser(userId)
            .flatMapRightWithEither { otherUser ->
                observeOtherUserTeam(otherUser)
                    .mapRight { team -> GetUserInfoResult.Success(otherUser, team) }
            }
            .mapToRightOr(GetUserInfoResult.Failure)
    }

    private suspend fun observeOtherUser(userId: UserId): Flow<Either<CoreFailure, OtherUser>> {
        return userRepository.getKnownUser(userId)
            .wrapStorageRequest()
            .map { either ->
                // If UserRepository.getKnownUser returns StorageFailure.DataNotFound means there is no such user in DB
                // so we need to fetch that user. After fetching and saving it into DB UserRepository.getKnownUser will emit a new value.
                // To handle such a case and do not loos any other CoreFailure from UserRepository.getKnownUser,
                // or from userRepository.fetchUsersByIds, we need to combine all of it into 1 data class and use later
                // for outputting a valid result.
                either.fold({ storageFailure ->
                    if (storageFailure is StorageFailure.DataNotFound) {
                        userRepository.fetchUsersByIds(setOf(userId))
                            .fold({ ObserveOtherUserResult(fetchUserError = it) }) { usersFound ->
                                if (usersFound) {
                                    // Fetched users are persisted
                                    ObserveOtherUserResult()
                                } else {
                                    // Users cannot be found
                                    ObserveOtherUserResult(fetchUserError = StorageFailure.DataNotFound)
                                }
                            }
                    } else {
                        ObserveOtherUserResult(getKnownUserError = storageFailure)
                    }
                }) { ObserveOtherUserResult(success = it) }
            }
            .filter {
                // false here means there was StorageFailure.DataNotFound during userRepository.getKnownUser,
                // userRepository.fetchUsersByIds was called and returned Either.Right(Unit),
                // which means user was saved into DB and userRepository.getKnownUser will emit it soon.
                it.isValid()
            }
            .map { it.toEither() }
    }

    /**
     * Users are allowed to fetch team details only if they are members of the same team
     * @see [UserType]
     */
    private suspend fun observeOtherUserTeam(otherUser: OtherUser): Flow<Either<CoreFailure, Team?>> {
        val teamId = otherUser.teamId
        return if (teamId != null && otherUser.userType in listOf(UserType.INTERNAL, UserType.OWNER)) {
            teamRepository.getTeam(teamId).map { localTeam ->
                if (localTeam == null) {
                    teamRepository.fetchTeamById(teamId)
                } else {
                    Either.Right(localTeam)
                }
            }
        } else {
            flowOf(Either.Right(null))
        }
    }
}

data class ObserveOtherUserResult(
    val getKnownUserError: CoreFailure? = null,
    val fetchUserError: CoreFailure? = null,
    val success: OtherUser? = null
) {
    fun isValid() = getKnownUserError != null || fetchUserError != null || success != null

    fun toEither(): Either<CoreFailure, OtherUser> =
        when {
            success != null -> Either.Right(success)
            getKnownUserError != null -> Either.Left(getKnownUserError)
            fetchUserError != null -> Either.Left(fetchUserError)
            else -> throw IllegalStateException("ObserveOtherUserResult.toEither: one of the fields should not be null.")
        }
}
