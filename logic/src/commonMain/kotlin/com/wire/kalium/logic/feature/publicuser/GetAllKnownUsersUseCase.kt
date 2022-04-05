package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

interface GetAllKnownUsersUseCase {
    suspend operator fun invoke(): Flow<List<OtherUser>>
}

class GetAllKnownUsersUseCaseImpl(private val userRepository: UserRepository) : GetAllKnownUsersUseCase {

    override suspend fun invoke(): Flow<List<OtherUser>> = userRepository.getAllKnownUsers()

}
