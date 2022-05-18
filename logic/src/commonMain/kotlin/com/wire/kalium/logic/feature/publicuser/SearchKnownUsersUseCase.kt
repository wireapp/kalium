package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult

interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): UserSearchResult
}

internal class SearchKnownUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository
) : SearchKnownUsersUseCase {

    override suspend operator fun invoke(searchQuery: String): UserSearchResult {
        return if (isUserLookingForHandle(searchQuery)) {
            searchUserRepository.searchKnownUsersByHandle(searchQuery)
        } else {
            searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(searchQuery)
        }
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.first() == '@'

}
