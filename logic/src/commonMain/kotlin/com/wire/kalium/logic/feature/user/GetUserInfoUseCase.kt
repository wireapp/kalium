package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.suspending

/**
 * Use case that allows getting the user details of a user
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

internal class GetUserInfoUseCaseImpl(private val userRepository: UserRepository) : GetUserInfoUseCase {

    override suspend fun invoke(userId: UserId): GetUserInfoResult = suspending {
        userRepository.fetchUserInfo(userId)
            .fold({
                GetUserInfoResult.Failure
            }, {
                GetUserInfoResult.Success(it)
            })
    }

}

sealed class GetUserInfoResult {
    class Success(val otherUser: OtherUser) : GetUserInfoResult()
    object Failure : GetUserInfoResult()
}
