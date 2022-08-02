package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.data.id.FEDERATION_REGEX
import com.wire.kalium.logic.data.id.parseIntoQualifiedID
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.UserRepository


interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String, searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default): Result
}

internal class SearchKnownUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val userRepository: UserRepository,
) : SearchKnownUsersUseCase {

    //TODO:handle failure
    override suspend fun invoke(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions
    ): Result {
        val searchResult = if (isUserLookingForHandle(searchQuery)) {
            searchUserRepository.searchKnownUsersByHandle(
                handle = searchQuery,
                searchUsersOptions = searchUsersOptions
            )
        } else {
            searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(
                searchQuery = if (searchQuery.matches(FEDERATION_REGEX))
                    searchQuery.parseIntoQualifiedID().value
                else searchQuery,
                searchUsersOptions = searchUsersOptions
            )
        }

        return Result.Success(excludeSelfUser(searchResult))
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.startsWith('@')

    //TODO: we should think about the way to exclude the self user on TABLE level
    private suspend fun excludeSelfUser(searchResult: UserSearchResult): UserSearchResult {
        val selfUser = userRepository.getSelfUser()

        return searchResult.copy(result = searchResult.result.filter { it.id != selfUser?.id })
    }

}
