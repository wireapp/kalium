package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

class GetSelfUserUseCase internal constructor(private val userRepository: UserRepository) {

    suspend operator fun invoke(): Flow<SelfUser> {
        return userRepository.observeSelfUser()
    }
}
