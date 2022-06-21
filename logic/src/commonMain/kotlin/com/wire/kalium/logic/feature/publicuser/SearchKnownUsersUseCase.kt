package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.id.FEDERATION_REGEX
import com.wire.kalium.logic.data.id.parseIntoQualifiedID
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.firstOrNull


interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): Result
}

internal class SearchKnownUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val userRepository: UserRepository,
) : SearchKnownUsersUseCase {

    //TODO:handle failure
    override suspend operator fun invoke(searchQuery: String): Result {
        val searchResult = if (isUserLookingForHandle(searchQuery)) {
            searchUserRepository.searchKnownUsersByHandle(searchQuery)
        } else {
            searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(
                if (searchQuery.matches(FEDERATION_REGEX))
                    searchQuery.parseIntoQualifiedID().value
                else searchQuery
            )
        }

        return Result.Success(excludeSelfUser(searchResult))
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.startsWith('@')

    //TODO: we should think about the way to exclude the self user on TABLE level
    private suspend fun excludeSelfUser(searchResult: UserSearchResult): UserSearchResult {
        val selfUser = userRepository.getSelfUser().firstOrNull()

        return searchResult.copy(result = searchResult.result.filter { it.id != selfUser?.id })
    }

}
