package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.other.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import kotlinx.coroutines.flow.Flow

interface GetKnownUserUseCase {
    suspend operator fun invoke(userId: UserId): Flow<OtherUser?>
}

class GetKnownUserUseCaseImpl(private val selfUserRepository: SelfUserRepository) : GetKnownUserUseCase {

    //TODO(qol): Better handle nullable OtherUser?
    override suspend fun invoke(userId: UserId): Flow<OtherUser?> = selfUserRepository.getKnownUser(userId)

}

