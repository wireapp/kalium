package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode


@Deprecated(
    "only network and non federated search",
    replaceWith = ReplaceWith(
        "com.wire.kalium.logic.feature.publicuser.SearchUsersUseCase",
        "com.wire.kalium.logic.feature.publicuser.SearchUsersUseCase"
    )
)
interface SearchUserDirectoryUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Result
}

@Deprecated(
    "only network and non federated search",
    replaceWith = ReplaceWith(
        "com.wire.kalium.logic.feature.publicuser.SearchUsersUseCaseImpl",
        "com.wire.kalium.logic.feature.publicuser.SearchUsersUseCaseImpl"
    )
)
internal class SearchUserDirectoryUseCaseImpl(
    private val searchUserRepository: SearchUserRepository
) : SearchUserDirectoryUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Result = searchUserRepository.searchUserDirectory(
        searchQuery = searchQuery,
        domain = domain,
        maxResultSize = maxResultSize
    ).fold({
        if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
            if (it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                return Result.Failure.InvalidRequest
            if (it.kaliumException.errorResponse.code == HttpStatusCode.NotFound.value)
                return Result.Failure.InvalidQuery
        }
        Result.Failure.Generic(it)
    }, {
        Result.Success(it)
    })
}

sealed class Result {
    data class Success(val userSearchResult: UserSearchResult) : Result()
    sealed class Failure : Result() {
        object InvalidQuery : Failure()
        object InvalidRequest : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

