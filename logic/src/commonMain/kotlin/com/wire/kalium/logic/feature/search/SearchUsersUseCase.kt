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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Use case for searching users.
 */
interface SearchUsersUseCase {
    /**
     * @param searchQuery The search query.
     * @param excludingMembersOfConversation The conversation to exclude its members from the search.
     * @param customDomain The custom domain to search in if null the search will be on the self user domain.
     */
    suspend operator fun invoke(
        searchQuery: String,
        excludingMembersOfConversation: ConversationId?,
        customDomain: String?
    ): SearchUserResult
}

class SearchUsersUseCaseImpl internal constructor(
    private val searchUserRepository: SearchUserRepository,
    private val selfUserId: UserId,
    private val maxRemoteSearchResultCount: Int
) : SearchUsersUseCase {
    override suspend operator fun invoke(
        searchQuery: String,
        excludingMembersOfConversation: ConversationId?,
        customDomain: String?
    ): SearchUserResult {
        return if (searchQuery.isBlank()) {
            SearchUserResult(
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
    ): SearchUserResult = coroutineScope {
        val cleanSearchQuery = searchQuery.trim().lowercase()

        val remoteResultsDeferred = async {
            searchUserRepository.searchUserRemoteDirectory(
                cleanSearchQuery,
                customDomain ?: selfUserId.domain,
                maxRemoteSearchResultCount,
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

        SearchUserResult.resolveLocalAndRemoteResult(localSearchResult, remoteResults)
    }
}
