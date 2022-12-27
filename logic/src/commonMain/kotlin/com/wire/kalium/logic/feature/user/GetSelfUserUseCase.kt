package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for retrieving the current user.
 * fixme: Rename to ObserveSelfUserUseCase
 */
interface GetSelfUserUseCase {

    /**
     * @return a [Flow] of the current user [SelfUser]
     */
    suspend operator fun invoke(): Flow<SelfUser>

}

internal class GetSelfUserUseCaseImpl internal constructor(private val userRepository: UserRepository) : GetSelfUserUseCase {

    override suspend operator fun invoke(): Flow<SelfUser> {
        return userRepository.observeSelfUser()
    }
}
