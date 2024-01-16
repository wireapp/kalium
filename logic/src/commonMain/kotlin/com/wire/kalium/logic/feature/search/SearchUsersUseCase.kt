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
package com.wire.kalium.logic.feature.search

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.ConversationMemberExcludedOptions
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Use case for searching users.
 */
class SearchUsersUseCase internal constructor(
    private val searchUserRepository: SearchUserRepository,
    private val selfUserId: UserId
) {
    suspend operator fun invoke(
        searchQuery: String,
        excludingMembersOfConversation: ConversationId?,
        customDomain: String?
    ): Result {
        return if (searchQuery.isBlank()) {
            Result(
                connected = searchUserRepository.getKnownContacts(excludingMembersOfConversation).getOrElse(emptyList()),
                notConnected = emptyList()
            )
        } else {
            handleSearch(searchQuery, excludingMembersOfConversation, customDomain)
        }
    }

    private suspend fun handleSearch(
        searchQuery: String,
        excludingConversation: ConversationId?,
        customDomain: String?
    ): Result = coroutineScope {
        val cleanSearchQuery = searchQuery.lowercase()

        val remoteResultsDeferred = async {
            searchUserRepository.searchUserRemoteDirectory(
                cleanSearchQuery,
                customDomain ?: selfUserId.domain,
                MAX_SEARCH_RESULTS,
                SearchUsersOptions(
                    conversationExcluded = excludingConversation?.let { ConversationMemberExcludedOptions.ConversationExcluded(it) }
                        ?: ConversationMemberExcludedOptions.None,
                    selfUserIncluded = false
                )
            ).map { userSearchResult ->
                userSearchResult.result.map {
                    UserSearchDetails(
                        id = it.id,
                        name = it.name,
                        completeAssetId = it.completePicture,
                        previewAssetId = it.previewPicture,
                        type = it.userType,
                        connectionStatus = it.connectionStatus,
                        handle = it.handle
                    )
                }
            }.getOrElse(emptyList())
                .associateBy { it.id }
                .toMutableMap()
        }

        val localSearchResultDeferred = async {
            searchUserRepository.searchLocalByName(cleanSearchQuery, excludingConversation)
                .getOrElse(emptyList())
                .associateBy { it.id }
                .toMutableMap()
        }

        val remoteResults = remoteResultsDeferred.await()
        val localSearchResult = localSearchResultDeferred.await()

        resolveLocalAndRemoteResult(localSearchResult, remoteResults)
    }

    private inline fun resolveLocalAndRemoteResult(
        localResult: MutableMap<UserId, UserSearchDetails>,
        remoteSearch: MutableMap<UserId, UserSearchDetails>
    ): Result {
        val updatedUser = mutableListOf<UserId>()
        remoteSearch.forEach { (userId, remoteUser) ->
            if (localResult.contains(userId) || (remoteUser.connectionStatus == ConnectionState.ACCEPTED)) {
                localResult[userId] = remoteUser
                updatedUser.add(userId)
            }
        }

        updatedUser.forEach { userId ->
            remoteSearch.remove(userId)
        }

        return Result(
            connected = localResult.values.toList(),
            notConnected = remoteSearch.values.toList()
        )
    }

    data class Result(
        val connected: List<UserSearchDetails>,
        val notConnected: List<UserSearchDetails>
    )

    private companion object {
        private const val MAX_SEARCH_RESULTS = 30
    }
}
