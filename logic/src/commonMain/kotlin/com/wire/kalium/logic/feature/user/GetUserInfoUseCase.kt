package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
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
                userRepository.fetchUserInfo(userId)
            }
        }
    }

    private suspend fun getOtherUserTeam(otherUser: OtherUser): Either<CoreFailure, Team?> {
        return if (otherUser.team != null) {
            val localTeam = teamRepository.getTeam(otherUser.team).firstOrNull()

            if (localTeam == null) {
                teamRepository.fetchTeamById(otherUser.team)
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
    object Failure : GetUserInfoResult()
}
