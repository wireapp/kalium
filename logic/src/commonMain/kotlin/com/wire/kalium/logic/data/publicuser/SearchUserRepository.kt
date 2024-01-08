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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.model.isTeamMember
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

internal interface SearchUserRepository {

    suspend fun searchKnownUsersByNameOrHandleOrEmail(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<UserSearchResult>

    suspend fun searchKnownUsersByHandle(
        handle: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<UserSearchResult>

    suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Either<NetworkFailure, UserSearchResult>

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
    private val metadataDAO: MetadataDAO,
    private val userDetailsApi: UserDetailsApi,
    private val teamsApi: TeamsApi,
    private val userSearchAPiWrapper: UserSearchApiWrapper,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val userEntityTypeMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper(),
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
    ): Either<NetworkFailure, UserSearchResult> =
        userSearchAPiWrapper.search(
            searchQuery,
            domain,
            maxResultSize,
            searchUsersOptions
        ).flatMap {
            val qualifiedIdList = it.documents.map { it.qualifiedID }
            val usersResponse =
                if (qualifiedIdList.isEmpty()) Either.Right(listOf())
                else wrapApiRequest {
                    userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(qualifiedIdList))
                }.map { listUsersDTO -> listUsersDTO.usersFound }

            usersResponse.flatMap { userProfileDTOList ->
                if (userProfileDTOList.isEmpty())
                    return Either.Right(UserSearchResult(emptyList()))

                val selfUser = getSelfUser()
                val (teamMembers, otherUsers) = userProfileDTOList
                    .partition { it.isTeamMember(selfUser.teamId?.value, selfUser.id.domain) }

                val teamMembersResponse =
                    if (selfUser.teamId == null || teamMembers.isEmpty()) Either.Right(emptyMap())
                    else wrapApiRequest {
                        teamsApi.getTeamMembersByIds(selfUser.teamId.value, TeamsApi.TeamMemberIdList(teamMembers.map { it.id.value }))
                    }.map { teamMemberList -> teamMemberList.members.associateBy { it.nonQualifiedUserId } }

                teamMembersResponse.map { teamMemberMap ->
                    // We need to store all found team members locally and not return them as they will be "known" users from now on.
                    teamMembers.map { userProfileDTO ->
                        userMapper.fromUserProfileDtoToUserEntity(
                            userProfile = userProfileDTO,
                            connectionState = ConnectionEntity.State.ACCEPTED,
                            userTypeEntity = userEntityTypeMapper.teamRoleCodeToUserType(
                                permissionCode = teamMemberMap[userProfileDTO.id.value]?.permissions?.own,
                                isService = userProfileDTO.service != null
                            )
                        )
                    }.let {
                        if (it.isNotEmpty()) {
                            userDAO.upsertUsers(it)
                            userDAO.upsertConnectionStatuses(it.associate { it.id to it.connectionStatus })
                        }
                    }

                    UserSearchResult(
                        otherUsers.map { userProfileDTO ->
                            userMapper.fromUserProfileDtoToOtherUser(userProfileDTO, selfUser)
                        }
                    )
                }
            }
        }

    // TODO: code duplication here for getting self user, the same is done inside
    // UserRepository, what would be best ?
    // creating SelfUserDao managing the UserEntity corresponding to SelfUser ?
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getSelfUser(): SelfUser {
        return metadataDAO.valueByKeyFlow(UserDataSource.SELF_USER_ID_KEY)
            .filterNotNull()
            .flatMapMerge { encodedValue ->
                val selfUserID: QualifiedIDEntity = Json.decodeFromString(string = encodedValue)

                userDAO.observeUserDetailsByQualifiedID(selfUserID)
                    .filterNotNull()
                    .map(userMapper::fromUserDetailsEntityToSelfUser)
            }.firstOrNull() ?: throw IllegalStateException()
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
