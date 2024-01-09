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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.SearchDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface SearchUserRepository {

    @Deprecated("to be deleted")
    suspend fun searchKnownUsersByNameOrHandleOrEmail(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<UserSearchResult>

    @Deprecated("to be deleted")
    suspend fun searchKnownUsersByHandle(
        handle: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<UserSearchResult>

    suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Either<CoreFailure, UserSearchResult>

    suspend fun initialSearchList(excludeConversation: ConversationId?): Either<StorageFailure, List<UserSearchDetails>>

    suspend fun searchLocalByName(
        name: String,
        excludeConversation: ConversationId?
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
    private val selfUserid: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper()
) : SearchUserRepository {

    override suspend fun searchKnownUsersByNameOrHandleOrEmail(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions
    ): Flow<UserSearchResult> =
        handleSearchUsersOptions(
            searchUsersOptions,
            excluded = { conversationId ->
                userDAO.getUsersDetailsNotInConversationByNameOrHandleOrEmail(
                    conversationId = conversationId.toDao(),
                    searchQuery = searchQuery
                )
            },
            default = {
                userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
                    searchQuery = searchQuery,
                    connectionStates = listOf(ConnectionEntity.State.ACCEPTED, ConnectionEntity.State.BLOCKED)
                )
            }
        )

    override suspend fun searchKnownUsersByHandle(
        handle: String,
        searchUsersOptions: SearchUsersOptions
    ): Flow<UserSearchResult> =
        handleSearchUsersOptions(
            searchUsersOptions,
            excluded = { conversationId ->
                userDAO.getUsersDetailsNotInConversationByHandle(
                    conversationId = conversationId.toDao(),
                    handle = handle
                )
            },
            default = {
                userDAO.getUserDetailsByHandleAndConnectionStates(
                    handle = handle,
                    connectionStates = listOf(ConnectionEntity.State.ACCEPTED, ConnectionEntity.State.BLOCKED)
                )
            }
        )

    override suspend fun searchUserDirectory(
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
                            userMapper.fromUserProfileDtoToOtherUser(userProfileDTO, selfUserid, selfTeamId)
                        }
                    )
                }
            }
        }

    override suspend fun initialSearchList(excludeConversation: ConversationId?): Either<StorageFailure, List<UserSearchDetails>> =
        wrapStorageRequest {
            if (excludeConversation == null) {
                searchDAO.initialSearchList()
            } else {
                searchDAO.initialSearchListExcludingAConversation(excludeConversation.toDao())
            }
        }.map { searchEntityList ->
            searchEntityList.map {
                UserSearchDetails(
                    id = it.id.toModel(),
                    name = it.name,
                    completeAssetId = it.completeAssetId?.toModel(),
                    type = userTypeMapper.fromUserTypeEntity(it.type),
                    previewAssetId = it.previewAssetId?.toModel(),
                    connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(it.connectionStatus)
                )
            }
        }

    override suspend fun searchLocalByName(
        name: String,
        excludeConversation: ConversationId?
    ): Either<StorageFailure, List<UserSearchDetails>> = wrapStorageRequest {
        if (excludeConversation == null) {
            searchDAO.searchList(name)
        } else {
            searchDAO.searchListExcludingAConversation(excludeConversation.toDao(), name)
        }
    }.map {
        it.map { searchEntity ->
            UserSearchDetails(
                id = searchEntity.id.toModel(),
                name = searchEntity.name,
                completeAssetId = searchEntity.completeAssetId?.toModel(),
                previewAssetId = searchEntity.previewAssetId?.toModel(),
                type = userTypeMapper.fromUserTypeEntity(searchEntity.type),
                connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(searchEntity.connectionStatus)
            )
        }
    }

    private suspend fun updateLocalUsers(
        userProfileDTOList: List<UserProfileDTO>,
    ) {
        userProfileDTOList
            .map { teamMembers ->
                PartialUserEntity(
                    id = teamMembers.id.toDao(),
                    name = teamMembers.name,
                    handle = teamMembers.handle,
                    email = teamMembers.email,
                    accentId = teamMembers.accentId,
                    supportedProtocols = teamMembers.supportedProtocols?.toDao()
                )
            }.also {
                if (it.isNotEmpty()) {
                    userDAO.updateUser(it)
                }
            }
    }

    private suspend fun handleSearchUsersOptions(
        localSearchUserOptions: SearchUsersOptions,
        excluded: suspend (conversationId: ConversationId) -> Flow<List<UserDetailsEntity>>,
        default: suspend () -> Flow<List<UserDetailsEntity>>
    ): Flow<UserSearchResult> {
        val listFlow = when (val searchOptions = localSearchUserOptions.conversationExcluded) {
            ConversationMemberExcludedOptions.None -> default()
            is ConversationMemberExcludedOptions.ConversationExcluded -> excluded(searchOptions.conversationId)
        }

        return listFlow.map {
            UserSearchResult(it.map(userMapper::fromUserDetailsEntityToOtherUser))
        }
    }

}
