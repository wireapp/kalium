package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.other.OtherUserRepository
import com.wire.kalium.logic.data.user.other.model.OtherUserSearchResult
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode


interface SearchUserDirectoryUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Result
}

internal class SearchUserDirectoryUseCaseImpl(
    private val contactRepository: OtherUserRepository
) : SearchUserDirectoryUseCase {

    override suspend fun invoke(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Result = contactRepository.searchRemoteUsers(
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
    data class Success(val otherUserSearchResult: OtherUserSearchResult) : Result()
    sealed class Failure : Result() {
        object InvalidQuery : Failure()
        object InvalidRequest : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()

    }
}

