package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.other.model.OtherUser
import com.wire.kalium.logic.data.user.self.SelfUserRepository

interface GetAllContactsUseCase {
    suspend operator fun invoke(): List<OtherUser>
}

class GetAllContactsUseCaseImpl(private val selfUserRepository: SelfUserRepository) : GetAllContactsUseCase {

    override suspend fun invoke(): List<OtherUser> = selfUserRepository.getAllContacts()

}
