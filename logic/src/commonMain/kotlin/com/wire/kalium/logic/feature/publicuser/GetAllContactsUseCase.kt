package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Operation that fetches all known users
 *
 * @return GetAllContactsResult with list of known users
 */
interface GetAllContactsUseCase {
    suspend operator fun invoke(): Flow<GetAllContactsResult>
}

internal class GetAllContactsUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetAllContactsUseCase {

    override suspend fun invoke(): Flow<GetAllContactsResult> = withContext(dispatchers.default) {
        userRepository.observeAllKnownUsers()
            .map { it.fold(GetAllContactsResult::Failure, GetAllContactsResult::Success) }
    }
}

sealed class GetAllContactsResult {
    data class Success(val allContacts: List<OtherUser>) : GetAllContactsResult()
    data class Failure(val storageFailure: StorageFailure) : GetAllContactsResult()
}
