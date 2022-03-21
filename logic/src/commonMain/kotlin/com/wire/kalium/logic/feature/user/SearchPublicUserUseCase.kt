package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.PublicUserRepository
import com.wire.kalium.logic.data.publicuser.model.PublicUserSearchResult
import com.wire.kalium.logic.functional.Either


interface SearchPublicUserUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, PublicUserSearchResult>
}

internal class SearchPublicUserUseCaseImpl(
    private val publicUserRepository: PublicUserRepository,
) : SearchPublicUserUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, PublicUserSearchResult> =
        publicUserRepository.searchPublicContact(
            searchQuery = searchQuery,
            domain = domain,
            maxResultSize = maxResultSize
        )
}

