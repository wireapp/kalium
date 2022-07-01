package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either

interface GetAllContactsUseCase {
    suspend operator fun invoke(): Either<StorageFailure, List<OtherUser>>
}

class GetAllContactsUseCaseImpl(private val userRepository: UserRepository) : GetAllContactsUseCase {

    override suspend fun invoke(): Either<StorageFailure, List<OtherUser>> = userRepository.getKnownUsers()

}
