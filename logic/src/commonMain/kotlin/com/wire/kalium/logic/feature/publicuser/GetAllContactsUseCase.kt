package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserRepository

interface GetAllContactsUseCase {
    suspend operator fun invoke(): List<OtherUser>
}

class GetAllContactsUseCaseImpl(private val userRepository: UserRepository) : GetAllContactsUseCase {

    override suspend fun invoke(): List<OtherUser> = userRepository.getAllContacts()

}
