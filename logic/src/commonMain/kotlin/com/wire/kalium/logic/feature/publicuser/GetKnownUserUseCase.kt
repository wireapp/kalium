package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

interface GetKnownUserUseCase {
    suspend operator fun invoke(userId: UserId): Flow<OtherUser?>
}

class GetKnownUserUseCaseImpl(private val userRepository: UserRepository) : GetKnownUserUseCase {

    //TODO(qol): Better handle nullable OtherUser?
    override suspend fun invoke(userId: UserId): Flow<OtherUser?> = userRepository.getKnownUser(userId)

}

