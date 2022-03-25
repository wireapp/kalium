package com.wire.kalium.logic.feature.wireuser.search

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.wireuser.SearchUserRepository
import com.wire.kalium.logic.functional.Either


interface SearchPublicWireUserUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, WireUserSearchResult>
}

internal class SearchPublicWireWireUserUseCaseImpl(
    private val searchUserRepository: SearchUserRepository
) : SearchPublicWireUserUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, WireUserSearchResult> =
        searchUserRepository.searchWireContact(
            searchQuery = searchQuery,
            domain = domain,
            maxResultSize = maxResultSize
        )
}

