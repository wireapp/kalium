package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode


interface SearchUsersUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int? = null,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Result
}

internal class SearchUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val connectionRepository: ConnectionRepository,
    private val qualifiedIdMapper: QualifiedIdMapper
) : SearchUsersUseCase {

    override suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Result {
        val qualifiedID = qualifiedIdMapper.fromStringToQualifiedID(searchQuery)

        return searchUserRepository.searchUserDirectory(
            searchQuery = qualifiedID.value,
            domain = qualifiedID.domain,
            maxResultSize = maxResultSize,
            searchUsersOptions = searchUsersOptions
        ).fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                return when (it.kaliumException.errorResponse.code) {
                    HttpStatusCode.BadRequest.value -> Result.Failure.InvalidRequest
                    HttpStatusCode.NotFound.value -> Result.Failure.InvalidQuery
                    else -> Result.Failure.Generic(it)
                }
            }
            Result.Failure.Generic(it)
        }, { response ->
            val connections = connectionRepository.getConnectionRequests()
            val usersWithConnectionStatus = response.copy(result = response.result
                .map { user ->
                    user.copy(
                        connectionStatus = connections.firstOrNull { user.id == it.qualifiedToId }?.status
                            ?: user.connectionStatus
                    )
                })

            Result.Success(usersWithConnectionStatus)
        })
    }
}
