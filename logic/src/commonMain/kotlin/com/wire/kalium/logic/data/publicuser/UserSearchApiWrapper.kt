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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchRequest
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal interface UserSearchApiWrapper {
    /*
     * Searches for users that match given the [searchQuery] using the API.
     * Depending on the [searchUsersOptions], the members of a conversation can be excluded.
     */
    suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResponse>
}

internal class UserSearchApiWrapperImpl(
    private val userSearchApi: UserSearchApi,
    private val conversationDAO: ConversationDAO,
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : UserSearchApiWrapper {

    override suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
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
            filter(userSearchResponse, searchUsersOptions)
        }

    private suspend fun filter(
        userSearchResponse: UserSearchResponse,
        searchUsersOptions: SearchUsersOptions
    ): UserSearchResponse {
        val selfUser = getSelfUser()

        // if we do not exclude the conversation members, we just return empty list
        val conversationMembersId = if (searchUsersOptions.conversationExcluded is ConversationMemberExcludedOptions.ConversationExcluded) {
            conversationDAO.getAllMembers(
                qualifiedID = searchUsersOptions.conversationExcluded.conversationId.toDao()
            ).firstOrNull()?.map { it.user.toModel() }
        } else {
            emptyList()
        }

        val filteredContactResponse = userSearchResponse.documents.filter { contactDTO ->
            val domainId = contactDTO.qualifiedID.toModel()

            var isConversationMember = false

            // if conversation members are empty it means there is nothing to exclude
            // from the search results, so we keep isConversationMember to be false
            if (!conversationMembersId.isNullOrEmpty()) {
                isConversationMember = conversationMembersId.contains(domainId)
            }

            // if we do not include the self user in the search options
            // we always set it to false, making the final OR operation
            // care only about isConversationMember value, since it is going to be
            // !(isConversationMember || 0) making it a operation based only on negated isConversationMember value
            // since !(0 || 0) = 1 , !(1 || 0) = 0
            val isSelfUser: Boolean = if (searchUsersOptions.selfUserIncluded) false else selfUser.id == domainId

            // negate it because that is exactly what we do not want to have in filter results
            !(isConversationMember || isSelfUser)
        }

        return userSearchResponse.copy(
            documents = filteredContactResponse,
            found = filteredContactResponse.size,
            returned = filteredContactResponse.size
        )
    }

    // TODO: code duplication here for getting self user, the same is done inside
    // UserRepository and SearchUserReopsitory what would be best ?
    // creating SelfUserDao managing the UserEntity corresponding to SelfUser ?
    @OptIn(FlowPreview::class)
    private suspend fun getSelfUser(): SelfUser {
        return metadataDAO.valueByKeyFlow(UserDataSource.SELF_USER_ID_KEY)
            .filterNotNull()
            .flatMapMerge { encodedValue ->
                val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)

                userDAO.getUserByQualifiedID(selfUserID)
                    .filterNotNull()
                    .map(userMapper::fromUserEntityToSelfUser)
            }.firstOrNull() ?: throw IllegalStateException()
    }
}
