package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

interface GetSelfUserUseCase {

    suspend operator fun invoke(): Flow<SelfUser>

}

internal class GetSelfUserUseCaseImpl internal constructor(private val userRepository: UserRepository) : GetSelfUserUseCase {

    override suspend operator fun invoke(): Flow<SelfUser> {
        return userRepository.observeSelfUser()
    }
}
