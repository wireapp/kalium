package com.wire.kalium.logic.feature.wireuser.search

import com.wire.kalium.logic.data.wireuser.SearchUserRepository
import kotlinx.coroutines.flow.Flow

interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): Flow<WireUserSearchResult>
}

internal class SearchKnownUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository
) : SearchKnownUsersUseCase {

    // TODO:this use case is going to be refactor once we return Either from DAO's
    override suspend operator fun invoke(searchQuery: String): Flow<WireUserSearchResult> =
        searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(searchQuery)

}
