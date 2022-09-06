package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.map

interface SearchUsersUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int? = null,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): SearchUserResult
}

internal class SearchUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val connectionRepository: ConnectionRepository,
    private val qualifiedIdMapper: QualifiedIdMapper
) : SearchUsersUseCase {

    private val observeConnectionRequests = connectionRepository.observeConnectionRequests()

    override suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): SearchUserResult {
        val qualifiedID = qualifiedIdMapper.fromStringToQualifiedID(searchQuery)

        return searchUserRepository.searchUserDirectory(
            searchQuery = qualifiedID.value,
            domain = qualifiedID.domain,
            maxResultSize = maxResultSize,
            searchUsersOptions = searchUsersOptions
        ).fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                return when (it.kaliumException.errorResponse.code) {
                    HttpStatusCode.BadRequest.value -> SearchUserResult.Failure.InvalidRequest
                    HttpStatusCode.NotFound.value -> SearchUserResult.Failure.InvalidQuery
                    else -> SearchUserResult.Failure.Generic(it)
                }
            }
            SearchUserResult.Failure.Generic(it)
        }, { response ->
            val usersWithConnectionStatusFlow = observeConnectionRequests
                .map { connections ->
                    response.map { user ->
                        connections.firstOrNull { user.id == it.qualifiedToId }
                            ?.status
                            ?.let { status -> user.copy(connectionStatus = status) }
                            ?: user
                    }
                }

            SearchUserResult.Success(usersWithConnectionStatusFlow)
        })
    }
}
