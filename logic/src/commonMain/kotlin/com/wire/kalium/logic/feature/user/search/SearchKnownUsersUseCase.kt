package com.wire.kalium.logic.feature.user.search

import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.WireUser
import com.wire.kalium.persistence.dao.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): Flow<WireUserSearchResult>
}

internal class SearchKnownUsersUseCaseImpl(
    private val userRepository: UserRepository
) : SearchKnownUsersUseCase {

    // TODO:this use case is going to be refactor once we return Either from DAO's
    override suspend operator fun invoke(searchQuery: String): Flow<WireUserSearchResult> =
        userRepository.searchKnownUsersByNameOrHandleOrEmail(searchQuery).map { WireUserSearchResult(it) }

}
