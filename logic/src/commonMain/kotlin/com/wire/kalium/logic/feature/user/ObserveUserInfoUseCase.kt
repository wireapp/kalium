package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMapRightWithEither
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.functional.mapToRightOr
import com.wire.kalium.logic.wrapStorageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Use case that allows observing the user details of a user locally,
 * or request it from API and save to DB, if there is no local data for such user.
 */
fun interface ObserveUserInfoUseCase {
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
            .mapLeft { storageFailure ->
                if (storageFailure is StorageFailure.DataNotFound) {
                    userRepository.fetchUsersByIds(setOf(userId))
                        .fold({ it }) { storageFailure }
                } else {
                    storageFailure
                }
            }
            .filter { either ->
                // We don't want to pass DataNotFound Error forward, cause in that case we'll fetch data from API
                either.fold({ it !is StorageFailure.DataNotFound }) { true }
            }
    }

    /**
     * Users are allowed to fetch team details only if they are members of the same team
     * @see [UserType]
     */
    private suspend fun observeOtherUserTeam(otherUser: OtherUser): Flow<Either<CoreFailure, Team?>> {
        return if (otherUser.teamId != null && otherUser.userType == UserType.INTERNAL) {
            teamRepository.getTeam(otherUser.teamId).map { localTeam ->
                if (localTeam == null) {
                    teamRepository.fetchTeamById(otherUser.teamId)
                } else {
                    Either.Right(localTeam)
                }
            }
        } else {
            flowOf(Either.Right(null))
        }
    }

}
