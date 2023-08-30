/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Searches for public users, aka users that are not in your contacts
 * For this reason, if we filter also users that are already contacts, and we exclude those results
 */
interface SearchPublicUsersUseCase {
    /**
     * @param searchQuery the query to search for¬
     * @param maxResultSize the maximum number of results to return
     * @param searchUsersOptions @see [SearchUsersOptions]
     * @return the [Flow] of [List] of [SearchUsersResult] if successful
     */
    suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int? = null,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<SearchUsersResult>
}

internal class SearchPublicUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val connectionRepository: ConnectionRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SearchPublicUsersUseCase {

    override suspend operator fun invoke(
        searchQuery: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Flow<SearchUsersResult> {
        val sanitizedSearchQuery = searchQuery.lowercase()
        val qualifiedID = qualifiedIdMapper.fromStringToQualifiedID(sanitizedSearchQuery)

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
            .flowOn(dispatcher.io)
    }
}
