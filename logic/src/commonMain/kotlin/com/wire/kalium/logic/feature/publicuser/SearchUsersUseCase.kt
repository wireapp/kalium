package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.FEDERATION_REGEX
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.parseIntoQualifiedID
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first


interface SearchUsersUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int? = null,
    ): Result
}

internal class SearchUsersUseCaseImpl(
    private val userRepository: UserRepository,
    private val searchUserRepository: SearchUserRepository,
) : SearchUsersUseCase {

    override suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int?
    ): Result {
        val isFederatedSearch = searchQuery.matches(FEDERATION_REGEX)
        val qualifiedID = if (isFederatedSearch) {
            searchQuery.parseIntoQualifiedID()
        } else {
            QualifiedID(searchQuery, userRepository.getSelfUser().first().id.domain)
        }
        return searchUserRepository.searchUserDirectory(
            searchQuery = qualifiedID.value,
            domain = qualifiedID.domain,
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


}



