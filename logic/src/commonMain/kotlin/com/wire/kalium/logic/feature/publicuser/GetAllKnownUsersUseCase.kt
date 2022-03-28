package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

interface GetAllKnownUsersUseCase {
    suspend operator fun invoke(): Flow<List<PublicUser>>
}

class GetAllKnownUsersUseCaseImpl(private val userRepository: UserRepository) : GetAllKnownUsersUseCase {

    override suspend fun invoke(): Flow<List<PublicUser>> = userRepository.getAllKnownUsers()

}
