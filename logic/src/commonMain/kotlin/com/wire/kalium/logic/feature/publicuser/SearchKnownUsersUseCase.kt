package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.other.OtherUserRepository
import com.wire.kalium.logic.data.user.other.model.OtherUserSearchResult

interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): OtherUserSearchResult
}

internal class SearchKnownUsersUseCaseImpl(
    private val contactRepository: OtherUserRepository
) : SearchKnownUsersUseCase {

    override suspend operator fun invoke(searchQuery: String): OtherUserSearchResult {
        return if (isUserLookingForHandle(searchQuery)) {
            contactRepository.searchKnownUsersByHandle(searchQuery)
        } else {
            contactRepository.searchKnownUsersByNameOrHandleOrEmail(searchQuery)
        }
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.first() == '@'

}
