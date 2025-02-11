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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.SearchDAO
import com.wire.kalium.persistence.dao.UserDAO

internal interface SearchUserRepository {
    suspend fun searchUserRemoteDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, UserSearchResult>

    suspend fun getKnownContacts(excludeConversation: ConversationId?): Either<StorageFailure, List<UserSearchDetails>>

    suspend fun searchLocalByName(
        name: String,
        excludeMembersOfConversation: ConversationId?
    ): Either<StorageFailure, List<UserSearchDetails>>

    suspend fun searchLocalByHandle(
        handle: String,
        excludeMembersOfConversation: ConversationId?
    ): Either<StorageFailure, List<UserSearchDetails>>

}

data class SearchUsersOptions(
    val conversationExcluded: ConversationMemberExcludedOptions,
    val selfUserIncluded: Boolean
) {
    companion object {
        val Default = SearchUsersOptions(
            conversationExcluded = ConversationMemberExcludedOptions.None,
            selfUserIncluded = false
        )
    }
}

sealed class ConversationMemberExcludedOptions {
    data object None : ConversationMemberExcludedOptions()
    data class ConversationExcluded(val conversationId: QualifiedID) : ConversationMemberExcludedOptions()
}

@Suppress("LongParameterList")
internal class SearchUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val searchDAO: SearchDAO,
    private val userDetailsApi: UserDetailsApi,
    private val userSearchAPiWrapper: UserSearchApiWrapper,
    private val selfUserId: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper()
) : SearchUserRepository {
    override suspend fun searchUserRemoteDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, UserSearchResult> =
        selfTeamIdProvider().flatMap { selfTeamId ->
            userSearchAPiWrapper.search(
                searchQuery,
                domain,
                maxResultSize,
                searchUsersOptions
            ).flatMap { userSearchResponse ->

                if (userSearchResponse.documents.isEmpty()) return Either.Right(UserSearchResult(listOf()))

                val qualifiedIdList = userSearchResponse.documents.map { it.qualifiedID }
                wrapApiRequest {
                    userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(qualifiedIdList))
                }.onSuccess { userProfileDTOList ->
                    updateLocalUsers(userProfileDTOList.usersFound)
                }.map { userProfileDTOList ->
                    UserSearchResult(
                        userProfileDTOList.usersFound.map { userProfileDTO ->
                            userMapper.fromUserProfileDtoToOtherUser(userProfileDTO, selfUserId, selfTeamId)
                        }
                    )
                }
            }
        }

    override suspend fun getKnownContacts(excludeConversation: ConversationId?): Either<StorageFailure, List<UserSearchDetails>> =
        wrapStorageRequest {
            if (excludeConversation == null) {
                searchDAO.getKnownContacts()
            } else {
                searchDAO.getKnownContactsExcludingAConversation(excludeConversation.toDao())
            }
        }.map {
            it.map(userMapper::fromSearchEntityToUserSearchDetails)
        }

    override suspend fun searchLocalByName(
        name: String,
        excludeMembersOfConversation: ConversationId?
    ): Either<StorageFailure, List<UserSearchDetails>> = wrapStorageRequest {
        if (excludeMembersOfConversation == null) {
            searchDAO.searchList(name)
        } else {
            searchDAO.searchListExcludingAConversation(excludeMembersOfConversation.toDao(), name)
        }
    }.map {
        it.map(userMapper::fromSearchEntityToUserSearchDetails)
    }

    override suspend fun searchLocalByHandle(
        handle: String,
        excludeMembersOfConversation: ConversationId?
    ): Either<StorageFailure, List<UserSearchDetails>> = if (excludeMembersOfConversation == null) {
        wrapStorageRequest {
            searchDAO.handleSearch(handle)
        }.map {
            it.map(userMapper::fromSearchEntityToUserSearchDetails)
        }
    } else {
        wrapStorageRequest {
            searchDAO.handleSearchExcludingAConversation(handle, excludeMembersOfConversation.toDao())
        }.map {
            it.map(userMapper::fromSearchEntityToUserSearchDetails)
        }
    }

    private suspend fun updateLocalUsers(
        userProfileDTOList: List<UserProfileDTO>,
    ) {
        userProfileDTOList
            .map { user ->
                PartialUserEntity(
                    id = user.id.toDao(),
                    name = user.name,
                    handle = user.handle,
                    email = user.email,
                    accentId = user.accentId,
                    supportedProtocols = user.supportedProtocols?.toDao()
                )
            }.also {
                if (it.isNotEmpty()) {
                    userDAO.updateUser(it)
                }
            }
    }
}
