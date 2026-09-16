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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.search.UserSearchRequest
import com.wire.kalium.network.api.authenticated.search.UserSearchResponse
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.persistence.dao.member.MemberDAO
import kotlinx.coroutines.flow.firstOrNull

internal interface UserSearchApiWrapper {
    /*
     * Searches for users that match given the [searchQuery] using the API.
     * Depending on the [searchUsersOptions], some users of the search result may be filtered out.
     */
    suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        selfTeamId: TeamId?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResponse>
}

internal class UserSearchApiWrapperImpl(
    private val userSearchApi: UserSearchApi,
    private val memberDAO: MemberDAO,
    private val selfUserId: UserId
) : UserSearchApiWrapper {

    override suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        selfTeamId: TeamId?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResponse> =
        wrapApiRequest {
            userSearchApi.search(
                UserSearchRequest(
                    searchQuery = searchQuery,
                    domain = domain,
                    maxResultSize = maxResultSize
                )
            )
        }.map { userSearchResponse ->
            filter(
                selfTeamId = selfTeamId,
                userSearchResponse = userSearchResponse,
                searchUsersOptions = searchUsersOptions
            )
        }

    private suspend fun filter(
        selfTeamId: TeamId?,
        userSearchResponse: UserSearchResponse,
        searchUsersOptions: SearchUsersOptions
    ): UserSearchResponse {

        // if we do not exclude the conversation members, we just return empty list
        val conversationMembersId = searchUsersOptions.conversationMembersExcluded?.let { conversationMembersExcluded ->
            memberDAO.observeConversationMembers(
                qualifiedID = conversationMembersExcluded.toDao()
            ).firstOrNull()?.map { it.user.toModel() }
        } ?: emptyList()

        val filteredContactResponse = userSearchResponse.documents.filter { contactDTO ->
            val isSelfUser = contactDTO.qualifiedID.toModel() == selfUserId
            val isExcludedConversationMember = conversationMembersId.contains(contactDTO.qualifiedID.toModel())
            val isSameTeamAndDomain = contactDTO.team == selfTeamId?.value && contactDTO.qualifiedID.domain == selfUserId.domain

            !isSelfUser && !isExcludedConversationMember && (!searchUsersOptions.onlySelfTeamAndDomain || isSameTeamAndDomain)
        }

        return userSearchResponse.copy(
            documents = filteredContactResponse,
            found = filteredContactResponse.size,
            returned = filteredContactResponse.size
        )
    }
}
