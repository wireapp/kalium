package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SearchPublicUsersUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int? = null,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<SearchUsersResult>
}

internal class SearchPublicUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val connectionRepository: ConnectionRepository,
    private val qualifiedIdMapper: QualifiedIdMapper
) : SearchPublicUsersUseCase {

    override suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Flow<SearchUsersResult> {
        val qualifiedID = qualifiedIdMapper.fromStringToQualifiedID(searchQuery)

        return connectionRepository.observeConnectionList()
            .map { connections ->
                searchUserRepository.searchUserDirectory(
                    searchQuery = qualifiedID.value,
                    domain = qualifiedID.domain,
                    maxResultSize = maxResultSize,
                    searchUsersOptions = searchUsersOptions
                ).fold({
                    if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                        when (it.kaliumException.errorResponse.code) {
                            HttpStatusCode.BadRequest.value -> SearchUsersResult.Failure.InvalidRequest
                            HttpStatusCode.NotFound.value -> SearchUsersResult.Failure.InvalidQuery
                            else -> SearchUsersResult.Failure.Generic(it)
                        }
                    } else {
                        SearchUsersResult.Failure.Generic(it)
                    }
                }, { response ->
                    val usersWithConnectionStatus = response.copy(result = response.result
                        .map { user ->
                            user.copy(
                                connectionStatus = connections.firstOrNull { user.id == it.qualifiedToId }?.status
                                    ?: user.connectionStatus
                            )
                        }
                        /** Users with accepted connection request should not be visible
                         *  in public search
                         */
                        .filter { it.connectionStatus != ConnectionState.ACCEPTED }
                    )

                    SearchUsersResult.Success(usersWithConnectionStatus)
                })
            }

    }
}
