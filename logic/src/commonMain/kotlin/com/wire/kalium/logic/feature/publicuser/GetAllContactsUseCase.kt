package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold


interface GetAllContactsUseCase {
    suspend operator fun invoke(): GetAllContactsResult
}

class GetAllContactsUseCaseImpl(private val userRepository: UserRepository) : GetAllContactsUseCase {

    override suspend fun invoke(): GetAllContactsResult =
        userRepository.getAllKnownUsers()
            .fold(GetAllContactsResult::Failure, GetAllContactsResult::Success)

}

sealed class GetAllContactsResult {
    data class Success(val allContacts: List<OtherUser>) : GetAllContactsResult()
    data class Failure(val storageFailure: StorageFailure) : GetAllContactsResult()
}
