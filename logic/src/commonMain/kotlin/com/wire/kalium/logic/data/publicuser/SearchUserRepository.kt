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
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.app.AppMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.persistence.dao.AppDAO
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

    suspend fun getKnownContacts(
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, List<UserSearchDetails>>

    suspend fun searchLocalByName(
        name: String,
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, List<UserSearchDetails>>

    suspend fun searchLocalByHandle(
        handle: String,
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, List<UserSearchDetails>>

}

internal data class SearchUsersOptions(
    val conversationMembersExcluded: ConversationId? = null, // By default, do not exclude any conversation members
    val onlySelfTeamAndDomain: Boolean = false, // By default, search users from all teams or no team
) {
    internal companion object {
        internal val Default = SearchUsersOptions()
    }
}

@Suppress("LongParameterList")
internal class SearchUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val searchDAO: SearchDAO,
    private val appDAO: AppDAO,
    private val userDetailsApi: UserDetailsApi,
    private val userSearchAPiWrapper: UserSearchApiWrapper,
    private val selfUserId: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val appMapper: AppMapper = MapperProvider.appMapper()
) : SearchUserRepository {
    override suspend fun searchUserRemoteDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, UserSearchResult> =
        selfTeamIdProvider().flatMap { selfTeamId ->
            userSearchAPiWrapper.search(
                searchQuery = searchQuery,
                domain = domain,
                maxResultSize = maxResultSize,
                selfTeamId = selfTeamId,
                searchUsersOptions = searchUsersOptions
            ).flatMap { userSearchResponse ->

                if (userSearchResponse.documents.isEmpty()) return Either.Right(UserSearchResult(listOf()))

                val qualifiedIdList = userSearchResponse.documents.map { it.qualifiedID }
                wrapApiRequest {
                    userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(qualifiedIdList))
                }.onSuccess { userProfileDTOList ->
                    updateLocalUsers(userProfileDTOList.usersFound)
                }.map { userProfileDTOList ->
                    val localConnectionStates = getLocalConnectionStates(userProfileDTOList.usersFound)
                    UserSearchResult(
                        userProfileDTOList
                            .usersFound
                            .filter { it.type != UserTypeDTO.APP }
                            .map { userProfileDTO ->
                                userMapper.fromUserProfileDtoToOtherUser(
                                    userProfileDTO,
                                    selfUserId,
                                    selfTeamId
                                ).let { remoteUser ->
                                    remoteUser.copy(
                                        connectionStatus = localConnectionStates[remoteUser.id] ?: remoteUser.connectionStatus
                                    )
                                }
                            }
                    )
                }
            }
        }

    override suspend fun getKnownContacts(
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, List<UserSearchDetails>> = selfTeamIdProvider().flatMap { selfTeamId ->
        wrapStorageRequest {
            searchDAO.getKnownContacts(
                excludeConversationId = searchUsersOptions.conversationMembersExcluded?.toDao(),
                onlyTeamId = if (searchUsersOptions.onlySelfTeamAndDomain) selfTeamId?.value else null,
                onlyDomain = if (searchUsersOptions.onlySelfTeamAndDomain) selfUserId.domain else null,
            )
        }
    }.map {
        it.map(userMapper::fromSearchEntityToUserSearchDetails)
    }

    override suspend fun searchLocalByName(
        name: String,
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, List<UserSearchDetails>> = selfTeamIdProvider().flatMap { selfTeamId ->
        wrapStorageRequest {
            searchDAO.searchByName(
                searchQuery = name,
                excludeConversationId = searchUsersOptions.conversationMembersExcluded?.toDao(),
                onlyTeamId = if (searchUsersOptions.onlySelfTeamAndDomain) selfTeamId?.value else null,
                onlyDomain = if (searchUsersOptions.onlySelfTeamAndDomain) selfUserId.domain else null,
            )
        }
    }.map {
        it.map(userMapper::fromSearchEntityToUserSearchDetails)
    }

    override suspend fun searchLocalByHandle(
        handle: String,
        searchUsersOptions: SearchUsersOptions
    ): Either<CoreFailure, List<UserSearchDetails>> = selfTeamIdProvider().flatMap { selfTeamId ->
        wrapStorageRequest {
            searchDAO.searchByHandle(
                searchQuery = handle,
                excludeConversationId = searchUsersOptions.conversationMembersExcluded?.toDao(),
                onlyTeamId = if (searchUsersOptions.onlySelfTeamAndDomain) selfTeamId?.value else null,
                onlyDomain = if (searchUsersOptions.onlySelfTeamAndDomain) selfUserId.domain else null,
            )
        }
    }.map {
        it.map(userMapper::fromSearchEntityToUserSearchDetails)
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

        userProfileDTOList
            .filter { it.type == UserTypeDTO.APP }
            .map { user ->
                appMapper.fromUserProfileToAppEntity(userProfileDTO = user)
            }.also {
                appDAO.upsertApps(it)
            }
    }

    private suspend fun getLocalConnectionStates(
        userProfileDTOList: List<UserProfileDTO>
    ): Map<UserId, ConnectionState> {
        if (userProfileDTOList.isEmpty()) return emptyMap()

        return wrapStorageRequest {
            userDAO.getUsersDetailsByQualifiedIDList(userProfileDTOList.map { it.id.toDao() })
        }.map { userDetails ->
            userDetails.associate { userDetail ->
                userMapper.fromUserDetailsEntityToOtherUser(userDetail).let { it.id to it.connectionStatus }
            }
        }.getOrElse(emptyMap())
    }
}
