package com.wire.kalium.logic.feature.wireuser.search

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.wireuser.WireUserRepository
import com.wire.kalium.logic.functional.Either


interface SearchPublicWireUserUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, WireUserSearchResult>
}

internal class SearchPublicWireWireUserUseCaseImpl(
    private val wireUserRepository: WireUserRepository
) : SearchPublicWireUserUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, WireUserSearchResult> =
        wireUserRepository.searchWireContact(
            searchQuery = searchQuery,
            domain = domain,
            maxResultSize = maxResultSize
        )
}

