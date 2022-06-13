package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.other.OtherUserRepository
import com.wire.kalium.logic.data.user.other.model.OtherUser
import com.wire.kalium.logic.data.user.self.SelfUserRepository

interface GetAllContactsUseCase {
    suspend operator fun invoke(): List<OtherUser>
}

class GetAllContactsUseCaseImpl(private val otherUserRepository: OtherUserRepository) : GetAllContactsUseCase {

    override suspend fun invoke(): List<OtherUser> = otherUserRepository.getAllKnownUsers()

}
