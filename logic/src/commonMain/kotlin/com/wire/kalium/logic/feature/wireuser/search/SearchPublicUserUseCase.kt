package com.wire.kalium.logic.feature.wireuser.search

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.wireuser.WireUserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map


interface SearchPublicUserUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, WireUserSearchResult>
}

internal class SearchPublicUserUseCaseImpl(
    private val wireUserRepository: WireUserRepository
) : SearchPublicUserUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, WireUserSearchResult> =
        wireUserRepository.searchPublicContact(
            searchQuery = searchQuery,
            domain = domain,
            maxResultSize = maxResultSize
        )
}

