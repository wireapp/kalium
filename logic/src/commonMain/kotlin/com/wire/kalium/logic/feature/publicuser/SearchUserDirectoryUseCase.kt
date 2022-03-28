package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.functional.Either


interface SearchUserDirectoryUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, UserSearchResult>
}

internal class SearchUserDirectoryUseCaseImpl(
    private val searchUserRepository: SearchUserRepository
) : SearchUserDirectoryUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, UserSearchResult> =
        searchUserRepository.searchUserDirectory(
            searchQuery = searchQuery,
            domain = domain,
            maxResultSize = maxResultSize
        )
}

