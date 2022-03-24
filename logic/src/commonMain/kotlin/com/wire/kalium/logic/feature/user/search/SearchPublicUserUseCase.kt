package com.wire.kalium.logic.feature.user.search

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
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
    private val userRepository: UserRepository,
) : SearchPublicUserUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, WireUserSearchResult> =
        userRepository.searchPublicContact(
            searchQuery = searchQuery,
            domain = domain,
            maxResultSize = maxResultSize
        ).map { WireUserSearchResult((it)) }
}

