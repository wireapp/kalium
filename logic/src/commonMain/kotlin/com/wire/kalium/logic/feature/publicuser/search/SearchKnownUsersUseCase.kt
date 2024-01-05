/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.id.FEDERATION_REGEX
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Searches for users that are contacts, in the same team, or in the same conversations.
 */
interface SearchKnownUsersUseCase {
    /**
     * @param searchQuery the query to search for if the search includes a "@" the domain is used to search in federated backends
     * @param searchUsersOptions @see [SearchUsersOptions]
     * @return the [Flow] of [List] of [SearchUsersResult] if successful
     */
    suspend operator fun invoke(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<SearchUsersResult>
}

internal class SearchKnownUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val userRepository: UserRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SearchKnownUsersUseCase {

    // TODO:handle failure
    override suspend fun invoke(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions
    ): Flow<SearchUsersResult> {
        val sanitizedSearchQuery = searchQuery.lowercase()
        return if (isUserLookingForHandle(sanitizedSearchQuery)) {
            searchUserRepository.searchKnownUsersByHandle(
                handle = sanitizedSearchQuery.sanitizeHandleSearchPattern(),
                searchUsersOptions = searchUsersOptions
            )
        } else {
            searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(
                searchQuery = if (sanitizedSearchQuery.matches(FEDERATION_REGEX)) {
                    sanitizedSearchQuery.run {
                        qualifiedIdMapper.fromStringToQualifiedID(this)
                    }.value
                } else {
                    sanitizedSearchQuery.removeSuffix("@")
                },
                searchUsersOptions = searchUsersOptions
            )
        }
            .map { SearchUsersResult.Success(excludeSelfUserAndDeletedUsers(it)) }
            .flowOn(dispatcher.io)
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.startsWith('@')

    // TODO: we should think about the way to exclude the self user on TABLE level
    private suspend fun excludeSelfUserAndDeletedUsers(searchResult: UserSearchResult): UserSearchResult {
        val selfUser = userRepository.getSelfUser()

        return searchResult.copy(result = searchResult.result.filter { it.id != selfUser?.id && !it.deleted })
    }

}

internal fun String.sanitizeHandleSearchPattern() = this.substringAfter("@").substringBefore("@")
