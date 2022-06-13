package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.other.OtherUserRepository
import com.wire.kalium.logic.data.user.other.model.OtherUserSearchResult

interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): OtherUserSearchResult
}

internal class SearchKnownUsersUseCaseImpl(
    private val otherUserRepository: OtherUserRepository
) : SearchKnownUsersUseCase {

    override suspend operator fun invoke(searchQuery: String): OtherUserSearchResult {
        return if (isUserLookingForHandle(searchQuery)) {
            otherUserRepository.searchKnownUsersByHandle(searchQuery)
        } else {
            otherUserRepository.searchKnownUsersByNameOrHandleOrEmail(searchQuery)
        }
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.first() == '@'

}
