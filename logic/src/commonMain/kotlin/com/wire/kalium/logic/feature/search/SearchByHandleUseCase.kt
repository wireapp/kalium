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
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Result of a search by handle.
 */
class SearchByHandleUseCase internal constructor(
    private val searchUserRepository: SearchUserRepository,
    private val selfUserId: UserId,
    private val maxRemoteSearchResultCount: Int
) {
    suspend operator fun invoke(
        searchHandle: String,
        excludingConversation: ConversationId?,
        customDomain: String?
    ): SearchUserResult = coroutineScope {
        val cleanSearchQuery = searchHandle
            .trim()
            .removePrefix("@")
            .lowercase()

        if (cleanSearchQuery.isBlank()) {
            return@coroutineScope SearchUserResult(emptyList(), emptyList())
        }

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
            searchUserRepository.searchLocalByHandle(
                cleanSearchQuery,
                excludingConversation
            ).getOrElse(emptyList())
                .associateBy { it.id }
                .toMutableMap()
        }

        val remoteResults = remoteResultsDeferred.await()
        val localSearchResult = localSearchResultDeferred.await()

        SearchUserResult.resolveLocalAndRemoteResult(localSearchResult, remoteResults)
    }
}
