package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


interface GetAllContactsUseCase {
    suspend operator fun invoke(): Flow<GetAllContactsResult>
}

internal class GetAllContactsUseCaseImpl internal constructor(
    private val userRepository: UserRepository
) : GetAllContactsUseCase {

    override suspend fun invoke(): Flow<GetAllContactsResult> =
        userRepository.observeAllKnownUsers()
            .map { it.fold(GetAllContactsResult::Failure, GetAllContactsResult::Success) }

}

sealed class GetAllContactsResult {
    data class Success(val allContacts: List<OtherUser>) : GetAllContactsResult()
    data class Failure(val storageFailure: StorageFailure) : GetAllContactsResult()
}
