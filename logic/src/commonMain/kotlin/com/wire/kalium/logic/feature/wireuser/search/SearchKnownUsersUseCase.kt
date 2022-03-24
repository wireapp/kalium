package com.wire.kalium.logic.feature.wireuser.search

import com.wire.kalium.logic.data.wireuser.WireUserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): Flow<WireUserSearchResult>
}

internal class SearchKnownUsersUseCaseImpl(
    private val wireUserRepository: WireUserRepository
) : SearchKnownUsersUseCase {

    // TODO:this use case is going to be refactor once we return Either from DAO's
    override suspend operator fun invoke(searchQuery: String): Flow<WireUserSearchResult> =
        wireUserRepository.searchKnownUsersByNameOrHandleOrEmail(searchQuery)

}
