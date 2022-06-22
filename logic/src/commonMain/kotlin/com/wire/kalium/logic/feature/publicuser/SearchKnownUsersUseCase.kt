package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.id.FEDERATION_REGEX
import com.wire.kalium.logic.data.id.parseIntoQualifiedID
import com.wire.kalium.logic.data.publicuser.SearchUserRepository

interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): Result
}

internal class SearchKnownUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository
) : SearchKnownUsersUseCase {

    override suspend operator fun invoke(searchQuery: String): Result {
        return if (isUserLookingForHandle(searchQuery)) {
            Result.Success(searchUserRepository.searchKnownUsersByHandle(searchQuery))
        } else {
            Result.Success(
                searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(
                    if (searchQuery.matches(FEDERATION_REGEX))
                        searchQuery.parseIntoQualifiedID().value
                    else searchQuery
                )
            )
        }
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.startsWith('@')

}
