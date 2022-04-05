package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

interface GetKnownUserUseCase {
    suspend operator fun invoke(userId: UserId): Flow<OtherUser?>
}

class GetKnownUserUseCaseImpl(private val userRepository: UserRepository) : GetKnownUserUseCase {

    //TODO: once we return Either here we could map the fact that the user is nullable to custom error
    // indicating it for example . NoUserFound
    override suspend fun invoke(userId: UserId): Flow<OtherUser?> = userRepository.getKnownUser(userId)

}

