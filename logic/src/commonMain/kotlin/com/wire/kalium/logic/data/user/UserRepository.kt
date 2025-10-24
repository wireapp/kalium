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

package com.wire.kalium.logic.data.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.legalhold.ListUsersLegalHoldConsent
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.network.api.authenticated.teams.TeamMemberDTO
import com.wire.kalium.network.api.authenticated.teams.TeamMemberIdList
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.UpgradePersonalToTeamApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.SelfUserDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.isTeamMember
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.mockative.Mockable
import com.wire.kalium.persistence.dao.member.MemberDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
@Mockable
interface UserRepository : SelfUserObservationProvider {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun insertSelfIncompleteUserWithOnlyEmail(email: String): Either<CoreFailure, Unit>

    /**
     * Fetches user information for all of users id stored in the DB
     */
    suspend fun fetchAllOtherUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(qualifiedUserIdList: Set<UserId>): Either<CoreFailure, Boolean>
    suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun observeSelfUserWithTeam(): Flow<Pair<SelfUser, Team?>>
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, Unit>
    suspend fun getSelfUser(): Either<StorageFailure, SelfUser>
    suspend fun observeAllKnownUsers(): Flow<Either<StorageFailure, List<OtherUser>>>
    suspend fun getKnownUser(userId: UserId): Flow<OtherUser?>
    suspend fun getKnownUserMinimized(userId: UserId): Either<StorageFailure, OtherUserMinimized>
    suspend fun getUsersWithOneOnOneConversation(): List<OtherUser>
    suspend fun observeUser(userId: UserId): Flow<User?>
    suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser>
    suspend fun updateOtherUserAvailabilityStatus(userId: UserId, status: UserAvailabilityStatus)
    fun observeAllKnownUsersNotInConversation(conversationId: ConversationId): Flow<Either<StorageFailure, List<OtherUser>>>

    /**
     * @return [Pair] of two Recipients lists, where [Pair.first] is the list of Recipients from my own team
     * and [Pair.second] is the list of all the other Recipients.
     */
    suspend fun getAllRecipients(): Either<CoreFailure, Pair<List<Recipient>, List<Recipient>>>
    suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit>
    suspend fun markUserAsDeletedAndRemoveFromGroupConversations(userId: UserId): Either<CoreFailure, List<ConversationId>>

    suspend fun markAsDeleted(userId: List<UserId>): Either<StorageFailure, Unit>

    /**
     * Marks federated user as defederated in order to hold conversation history
     * when backends stops federating.
     */
    suspend fun defederateUser(userId: UserId): Either<CoreFailure, Unit>

    // TODO: move to migration repo
    suspend fun insertUsersIfUnknown(users: List<User>): Either<StorageFailure, Unit>
    suspend fun fetchUserInfo(userId: UserId): Either<CoreFailure, Unit>

    /**
     * Updates users without metadata from the server.
     */
    suspend fun syncUsersWithoutMetadata(): Either<CoreFailure, Unit>

    /**
     * Removes broken user asset to avoid fetching it until next sync.
     */
    suspend fun removeUserBrokenAsset(qualifiedID: QualifiedID): Either<CoreFailure, Unit>

    /**
     * Gets users summary by their ids.
     */
    suspend fun getUsersSummaryByIds(userIds: List<QualifiedID>): Either<StorageFailure, List<UserSummary>>

    suspend fun updateSupportedProtocols(protocols: Set<SupportedProtocol>): Either<CoreFailure, Unit>

    suspend fun updateActiveOneOnOneConversation(userId: UserId, conversationId: ConversationId): Either<CoreFailure, Unit>

    suspend fun updateActiveOneOnOneConversationIfNotSet(userId: UserId, conversationId: ConversationId): Either<CoreFailure, Unit>

    suspend fun isAtLeastOneUserATeamMember(userId: List<UserId>, teamId: TeamId): Either<StorageFailure, Boolean>

    suspend fun insertOrIgnoreIncompleteUsers(userIds: List<QualifiedID>): Either<StorageFailure, Unit>

    suspend fun fetchUsersLegalHoldConsent(userIds: Set<UserId>): Either<CoreFailure, ListUsersLegalHoldConsent>

    suspend fun getOneOnOnConversationId(userId: QualifiedID): Either<StorageFailure, ConversationId>
    suspend fun getUsersMinimizedByQualifiedIDs(userIds: List<UserId>): Either<StorageFailure, List<OtherUserMinimized>>
    suspend fun getNameAndHandle(userId: UserId): Either<StorageFailure, NameAndHandle>
    suspend fun migrateUserToTeam(teamName: String): Either<CoreFailure, CreateUserTeam>
    suspend fun updateTeamId(userId: UserId, teamId: TeamId): Either<StorageFailure, Unit>
    suspend fun isClientMlsCapable(userId: UserId, clientId: ClientId): Either<StorageFailure, Boolean>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class UserDataSource internal constructor(
    private val userDAO: UserDAO,
    private val clientDAO: ClientDAO,
    private val memberDAO: MemberDAO,
    private val selfApi: SelfApi,
    private val userDetailsApi: UserDetailsApi,
    private val upgradePersonalToTeamApi: UpgradePersonalToTeamApi,
    private val teamsApi: TeamsApi,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val legalHoldHandler: LegalHoldHandler,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val teamMapper: TeamMapper = MapperProvider.teamMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val userTypeEntityMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
) : UserRepository {

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = wrapApiRequest { selfApi.getSelfInfo() }
        .flatMap { selfUserDTO ->
            selfUserDTO.teamId.let { selfUserTeamId ->
                if (selfUserTeamId.isNullOrEmpty()) Either.Right(null)
                else wrapApiRequest { teamsApi.getTeamMember(selfUserTeamId, selfUserId.value) }
            }.map { selfUserDTO to it }
        }
        .flatMap { (userDTO, teamMemberDTO) ->
            if (userDTO.deleted == true) {
                Either.Left(SelfUserDeleted)
            } else {
                updateSelfUserProviderAccountInfo(userDTO)
                    .map {
                        userMapper.fromSelfUserDtoToUserEntity(
                            userDTO = userDTO,
                            connectionState = ConnectionEntity.State.ACCEPTED,
                            userTypeEntity = userTypeEntityMapper.teamRoleCodeToUserType(teamMemberDTO?.permissions?.own)
                        )
                    }
                    .flatMap { userEntity ->
                        wrapStorageRequest { userDAO.upsertUser(userEntity) }
                    }
            }
        }

    override suspend fun insertSelfIncompleteUserWithOnlyEmail(email: String): Either<CoreFailure, Unit> = wrapStorageRequest {
        userDAO.insertOrIgnoreIncompleteUserWithOnlyEmail(selfUserId.toDao(), email)
    }

    private suspend fun updateSelfUserProviderAccountInfo(userDTO: SelfUserDTO): Either<StorageFailure, Unit> =
        sessionRepository.updateSsoIdAndScimInfo(userDTO.id.toModel(), idMapper.toSsoId(userDTO.ssoID), userDTO.managedByDTO)

    override suspend fun getKnownUser(userId: UserId): Flow<OtherUser?> =
        userDAO.observeUserDetailsByQualifiedID(qualifiedID = userId.toDao())
            .map { userEntity ->
                userEntity?.let { userMapper.fromUserDetailsEntityToOtherUser(userEntity) }
            }

    override suspend fun getUsersWithOneOnOneConversation(): List<OtherUser> {
        return userDAO.getUsersWithOneOnOneConversation()
            .map(userMapper::fromUserEntityToOtherUser)
    }

    override suspend fun fetchAllOtherUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.allOtherUsersId().map(UserIDEntity::toModel).toSet()
        return fetchUsersByIds(ids).map { }
    }

    override suspend fun fetchUserInfo(userId: UserId) =
        wrapApiRequest { userDetailsApi.getUserInfo(userId.toApi()) }
            .flatMap { userProfileDTO ->
                fetchTeamMembersByIds(listOf(userProfileDTO))
                    .flatMap { persistUsers(listOf(userProfileDTO), it) }
            }
            .onFailure {
                userDAO.insertOrIgnoreIncompleteUsers(listOf(userId.toDao()))
            }

    private suspend fun fetchUsersByIdsReturningListUsersDTO(qualifiedUserIdList: Set<UserId>): Either<CoreFailure, ListUsersDTO> =
        if (qualifiedUserIdList.isEmpty()) {
            Either.Right(ListUsersDTO(emptyList(), emptyList()))
        } else {
            qualifiedUserIdList
                .chunked(BATCH_SIZE)
                .foldToEitherWhileRight(ListUsersDTO(emptyList(), emptyList())) { chunk, usersDTO ->
                    wrapApiRequest {
                        kaliumLogger.d("Fetching ${chunk.size} users")
                        userDetailsApi.getMultipleUsers(
                            ListUserRequest.qualifiedIds(chunk.map { userId -> userId.toApi() })
                        )
                    }.map {
                        kaliumLogger.d("Found ${it.usersFound.size} users and ${it.usersFailed.size} failed users")
                        usersDTO.copy(
                            usersFound = (usersDTO.usersFound + it.usersFound).distinct(),
                            usersFailed = (usersDTO.usersFailed + it.usersFailed).distinct(),
                        )
                    }
                }
                .flatMapLeft { error ->
                    if (error is NetworkFailure.FederatedBackendFailure.FederationNotEnabled) {
                        val domains = qualifiedUserIdList
                            .filterNot { it.domain == selfUserId.domain }
                            .map { it.domain.obfuscateDomain() }
                            .toSet()
                        val domainNames = domains.joinToString(separator = ", ")
                        kaliumLogger.e("User ids contains different domains when federation is not enabled by backend: $domainNames")
                        wrapApiRequest {
                            userDetailsApi.getMultipleUsers(
                                ListUserRequest.qualifiedIds(
                                    qualifiedUserIdList.filter { it.domain == selfUserId.domain }
                                    .map { userId -> userId.toApi() }
                                )
                            )
                        }
                    } else {
                        Either.Left(error)
                    }
                }
                .flatMap { listUserProfileDTO ->
                    if (listUserProfileDTO.usersFailed.isNotEmpty()) {
                        kaliumLogger.d("Handling ${listUserProfileDTO.usersFailed.size} failed users")
                        persistIncompleteUsers(listUserProfileDTO.usersFailed)
                    }
                    fetchTeamMembersByIds(listUserProfileDTO.usersFound)
                        .flatMap { persistUsers(listUserProfileDTO.usersFound, it) }
                        .map { listUserProfileDTO }
                }
        }

    override suspend fun fetchUsersByIds(qualifiedUserIdList: Set<UserId>): Either<CoreFailure, Boolean> =
        fetchUsersByIdsReturningListUsersDTO(qualifiedUserIdList).map { it.usersFound.isNotEmpty() }

    private suspend fun fetchTeamMembersByIds(userProfileList: List<UserProfileDTO>): Either<CoreFailure, List<TeamMemberDTO>> {
        val selfUserDomain = selfUserId.domain
        val selfUserTeamId = selfTeamIdProvider().getOrNull()
        val teamMemberIds = userProfileList.filter {
            it.isTeamMember(selfUserTeamId?.value, selfUserDomain)
        }.map { it.id.value }
        return if (selfUserTeamId == null || teamMemberIds.isEmpty()) Either.Right(emptyList())
        else teamMemberIds
            .chunked(BATCH_SIZE)
            .foldToEitherWhileRight(emptyList()) { chunk, acc ->
                wrapApiRequest {
                    kaliumLogger.d("Fetching ${chunk.size} team members")
                    teamsApi.getTeamMembersByIds(selfUserTeamId.value, TeamMemberIdList(chunk))
                }.map {
                    kaliumLogger.d("Found ${it.members.size} team members")
                    (acc + it.members).distinct()
                }
            }
    }

    private suspend fun persistIncompleteUsers(usersFailed: List<NetworkQualifiedId>) = wrapStorageRequest {
        userDAO.insertOrIgnoreUsers(usersFailed.map { userMapper.fromFailedUserToEntity(it) })
    }

    private suspend fun persistUsers(
        listUserProfileDTO: List<UserProfileDTO>,
        listTeamMemberDTO: List<TeamMemberDTO>,
    ): Either<CoreFailure, Unit> {
        val mapTeamMemberDTO = listTeamMemberDTO.associateBy { it.nonQualifiedUserId }
        val selfUserTeamId = selfTeamIdProvider().getOrNull()?.value
        val teamMembers = listUserProfileDTO
            .filter { userProfileDTO -> mapTeamMemberDTO.containsKey(userProfileDTO.id.value) }
            .map { userProfileDTO ->
                userMapper.fromUserProfileDtoToUserEntity(
                    userProfile = userProfileDTO,
                    connectionState = ConnectionEntity.State.ACCEPTED,
                    userTypeEntity =
                    if (userProfileDTO.service != null) UserTypeEntity.SERVICE
                    else userTypeEntityMapper.teamRoleCodeToUserType(mapTeamMemberDTO[userProfileDTO.id.value]?.permissions?.own)
                )
            }
        val otherUsers = listUserProfileDTO
            .filter { userProfileDTO -> !mapTeamMemberDTO.containsKey(userProfileDTO.id.value) }
            .map { userProfileDTO ->
                userMapper.fromUserProfileDtoToUserEntity(
                    userProfile = userProfileDTO,
                    connectionState = ConnectionEntity.State.NOT_CONNECTED, // this won't be updated, just to avoid a null value
                    userTypeEntity = userTypeEntityMapper.fromTeamAndDomain(
                        otherUserDomain = userProfileDTO.id.domain,
                        selfUserTeamId = selfUserTeamId,
                        otherUserTeamId = userProfileDTO.teamId,
                        selfUserDomain = selfUserId.domain,
                        isService = userProfileDTO.service != null
                    )
                )
            }
        return listUserProfileDTO
            .map {
                legalHoldHandler.handleUserFetch(it.id.toModel(), it.legalHoldStatus == LegalHoldStatusDTO.ENABLED)
            }
            .foldToEitherWhileRight(Unit) { value, _ -> value }
            .flatMap {
                wrapStorageRequest {
                    if (teamMembers.isNotEmpty()) {
                        userDAO.upsertUsers(teamMembers)
                        userDAO.upsertConnectionStatuses(teamMembers.associate { it.id to it.connectionStatus })
                    }
                    if (otherUsers.isNotEmpty()) {
                        userDAO.upsertUsers(otherUsers)
                    }
                }
            }
    }

    override suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit> = wrapStorageRequest {
        val qualifiedIDList = ids.map { it.toDao() }
        val knownUsers = userDAO.getUsersDetailsByQualifiedIDList(ids.map { it.toDao() })
        // TODO we should differentiate users with incomplete data not by checking if name isNullOrBlank
        // TODO but add separate property (when federated backend is down)
        qualifiedIDList.filterNot { knownUsers.any { userEntity -> userEntity.id == it && !userEntity.name.isNullOrBlank() } }
    }.flatMap { missingIds ->
        if (missingIds.isEmpty()) Either.Right(Unit)
        else fetchUsersByIds(missingIds.map { it.toModel() }.toSet()).map { }
    }

    override suspend fun observeSelfUser(): Flow<SelfUser> = userDAO.observeUserDetailsByQualifiedID(selfUserId.toDao()).filterNotNull()
        .map(userMapper::fromUserDetailsEntityToSelfUser)

    override suspend fun observeSelfUserWithTeam(): Flow<Pair<SelfUser, Team?>> {
        return userDAO.getUserDetailsWithTeamByQualifiedID(selfUserId.toDao()).filterNotNull()
            .map { (user, team) ->
                userMapper.fromUserDetailsEntityToSelfUser(user) to team?.let { teamMapper.fromDaoModelToTeam(it) }
            }
    }

    @Deprecated(
        message = "Create a dedicated function to update the corresponding user property, instead of updating the whole user",
        replaceWith = ReplaceWith("eg: updateSelfDisplayName(displayName: String)")
    )
    // FIXME(refactor): create a dedicated function to update avatar, as this is the only usage of this function.
    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, Unit> {
        val updateRequest = userMapper.fromModelToUpdateApiModel(newName, newAccent, newAssetId)
        return wrapApiRequest { selfApi.updateSelf(updateRequest) }
            .map { userMapper.fromUpdateRequestToPartialUserEntity(updateRequest, selfUserId) }
            .flatMap { partialUserEntity ->
                wrapStorageRequest {
                    userDAO.updateUser(partialUserEntity)
                }
            }.map { }
    }

    override suspend fun getSelfUser(): Either<StorageFailure, SelfUser> = wrapStorageRequest {
        userDAO.getUserDetailsByQualifiedID(selfUserId.toDao())?.let {
            userMapper.fromUserDetailsEntityToSelfUser(it)
        }
    }

    override suspend fun observeAllKnownUsers(): Flow<Either<StorageFailure, List<OtherUser>>> {
        val selfUserId = selfUserId.toDao()
        return userDAO.observeAllUsersDetailsByConnectionStatus(connectionState = ConnectionEntity.State.ACCEPTED)
            .wrapStorageRequest()
            .mapRight { users ->
                users
                    .filter { it.id != selfUserId && !it.deleted && !it.hasIncompleteMetadata }
                    .map { userEntity -> userMapper.fromUserDetailsEntityToOtherUser(userEntity) }
            }
    }

    override suspend fun getKnownUserMinimized(userId: UserId) = wrapStorageRequest {
        userDAO.getUserMinimizedByQualifiedID(
            qualifiedID = userId.toDao()
        )?.let {
            userMapper.fromUserEntityToOtherUserMinimized(it)
        }
    }

    override suspend fun getUsersMinimizedByQualifiedIDs(userIds: List<UserId>) = wrapStorageRequest {
        userDAO.getUsersMinimizedByQualifiedIDs(
            qualifiedIDs = userIds.map { it.toDao() }
        ).map(userMapper::fromUserEntityToOtherUserMinimized)
    }

    override suspend fun observeUser(userId: UserId): Flow<User?> =
        userDAO.observeUserDetailsByQualifiedID(qualifiedID = userId.toDao())
            .map { userEntity ->
                if (userId == selfUserId) {
                    userEntity?.let { userMapper.fromUserDetailsEntityToSelfUser(userEntity) }
                } else {
                    userEntity?.let { userMapper.fromUserDetailsEntityToOtherUser(userEntity) }
                }
            }

    override suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser> =
        selfTeamIdProvider().flatMap { selfTeamId ->
            wrapApiRequest { userDetailsApi.getUserInfo(userId.toApi()) }.map { userProfileDTO ->
                userMapper.fromUserProfileDtoToOtherUser(userProfileDTO, selfUserId, selfTeamId)
            }
        }

    override suspend fun updateOtherUserAvailabilityStatus(userId: UserId, status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(userId.toDao(), availabilityStatusMapper.fromModelAvailabilityStatusToDao(status))
    }

    override suspend fun updateSupportedProtocols(protocols: Set<SupportedProtocol>): Either<CoreFailure, Unit> {
        return wrapApiRequest { selfApi.updateSupportedProtocols(protocols.map { it.toApi() }) }
            .flatMap {
                wrapStorageRequest {
                    userDAO.updateUserSupportedProtocols(selfUserId.toDao(), protocols.map { it.toDao() }.toSet())
                }
            }
    }

    override suspend fun updateActiveOneOnOneConversation(userId: UserId, conversationId: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest { userDAO.updateActiveOneOnOneConversation(userId.toDao(), conversationId.toDao()) }

    override suspend fun updateActiveOneOnOneConversationIfNotSet(
        userId: UserId,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        userDAO.updateActiveOneOnOneConversationIfNotSet(userId.toDao(), conversationId.toDao())
    }

    override suspend fun isAtLeastOneUserATeamMember(userId: List<UserId>, teamId: TeamId) = wrapStorageRequest {
        userDAO.isAtLeastOneUserATeamMember(userId.map { it.toDao() }, teamId.value)
    }

    override suspend fun insertOrIgnoreIncompleteUsers(userIds: List<QualifiedID>) = wrapStorageRequest {
        userDAO.insertOrIgnoreIncompleteUsers(userIds.map { it.toDao() })
    }

    override suspend fun fetchUsersLegalHoldConsent(userIds: Set<UserId>): Either<CoreFailure, ListUsersLegalHoldConsent> =
        fetchUsersByIdsReturningListUsersDTO(userIds).map { listUsersDTO ->
            listUsersDTO.usersFound
                .partition { it.legalHoldStatus != LegalHoldStatusDTO.NO_CONSENT }
                .let { (usersWithConsent, usersWithoutConsent) ->
                    ListUsersLegalHoldConsent(
                        usersWithConsent = usersWithConsent.map { it.id.toModel() to it.teamId?.let { TeamId(it) } },
                        usersWithoutConsent = usersWithoutConsent.map { it.id.toModel() },
                        usersFailed = listUsersDTO.usersFailed.map { it.toModel() }
                    )
                }
        }

    override fun observeAllKnownUsersNotInConversation(
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, List<OtherUser>>> {
        return userDAO.observeUsersDetailsNotInConversation(conversationId.toDao())
            .wrapStorageRequest()
            .mapRight { users ->
                users
                    .filter { !it.deleted && !it.hasIncompleteMetadata }
                    .map { userMapper.fromUserDetailsEntityToOtherUser(it) }
            }
    }

    override suspend fun getAllRecipients(): Either<CoreFailure, Pair<List<Recipient>, List<Recipient>>> =
        selfTeamIdProvider().flatMap { teamId ->
            val teamMateIds = teamId?.value?.let { selfTeamId ->
                wrapStorageRequest { userDAO.getAllUsersDetailsByTeam(selfTeamId).map { it.id.toModel() } }
            }?.getOrNull() ?: listOf()

            val allUsersWithConversations = memberDAO.getAllMembers().map { it.toModel() }

            wrapStorageRequest {
                memberMapper.fromMapOfClientsEntityToRecipients(clientDAO.selectAllClients())
            }.map { allRecipients ->
                val teamRecipients = mutableListOf<Recipient>()
                val otherRecipients = mutableListOf<Recipient>()

                allRecipients.forEach {
                    if (teamMateIds.contains(it.id)) teamRecipients.add(it)
                    else otherRecipients.add(it)
                }

                val allRecipientsIds = allRecipients.mapTo(mutableSetOf()) { it.id }

                // Ensure we add all users in the list even if they do not have a client id stored locally.
                otherRecipients.addAll(
                    allUsersWithConversations
                        .filterNot { allRecipientsIds.contains(it) }
                        .map { Recipient(it, emptyList()) }
                )

                teamRecipients.toList() to otherRecipients.toList()
            }
        }

    override suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit> = wrapStorageRequest {
        userDAO.updateUser(userMapper.fromUserUpdateEventToPartialUserEntity(event))
    }.onFailure {
        Either.Left(StorageFailure.DataNotFound)
    }.onSuccess {
        Either.Right(Unit)
    }

    override suspend fun markUserAsDeletedAndRemoveFromGroupConversations(
        userId: UserId
    ): Either<CoreFailure, List<ConversationId>> =
        wrapStorageRequest {
            userDAO.markUserAsDeletedAndRemoveFromGroupConv(userId.toDao())
        }.map { it.map(ConversationIDEntity::toModel) }

    override suspend fun markAsDeleted(userId: List<UserId>): Either<StorageFailure, Unit> = wrapStorageRequest {
        userDAO.markAsDeleted(userId.map { it.toDao() })
    }

    override suspend fun defederateUser(userId: UserId): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            userDAO.markUserAsDefederated(userId.toDao())
        }
    }

    override suspend fun insertUsersIfUnknown(users: List<User>): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userDAO.insertOrIgnoreUsers(
                users.map { user ->
                    when (user) {
                        is OtherUser -> userMapper.fromOtherToUserEntity(user)
                        is SelfUser -> userMapper.fromSelfUserToUserEntity(user)
                    }
                }
            )
        }

    override suspend fun syncUsersWithoutMetadata(): Either<CoreFailure, Unit> = wrapStorageRequest {
        userDAO.getUsersDetailsWithoutMetadata()
    }.flatMap { usersWithoutMetadata ->
        kaliumLogger.d("Numbers of users to refresh: ${usersWithoutMetadata.size}")
        val userIds = usersWithoutMetadata.map { it.id.toModel() }.toSet()
        fetchUsersByIds(userIds).map { }
    }

    override suspend fun removeUserBrokenAsset(qualifiedID: QualifiedID) = wrapStorageRequest {
        userDAO.removeUserAsset(qualifiedID.toDao())
    }

    override suspend fun getUsersSummaryByIds(userIds: List<QualifiedID>): Either<StorageFailure, List<UserSummary>> =
        wrapStorageRequest {
            userDAO.getUsersDetailsByQualifiedIDList(userIds.map { it.toDao() }).map {
                userMapper.fromEntityToUserSummary(it.toSimpleEntity())
            }
        }

    override suspend fun getOneOnOnConversationId(userId: QualifiedID): Either<StorageFailure, ConversationId> = wrapStorageRequest {
        userDAO.getOneOnOnConversationId(userId.toDao())?.toModel()
    }

    override suspend fun getNameAndHandle(userId: UserId): Either<StorageFailure, NameAndHandle> = wrapStorageRequest {
        userDAO.getNameAndHandle(userId.toDao())
    }.map { NameAndHandle.fromEntity(it) }

    override suspend fun migrateUserToTeam(teamName: String): Either<CoreFailure, CreateUserTeam> {
        return wrapApiRequest { upgradePersonalToTeamApi.migrateToTeam(teamName) }.map { dto ->
            CreateUserTeam(dto.teamId, dto.teamName)
        }
            .onSuccess {
                fetchSelfUser()
            }
            .onFailure { failure ->
                kaliumLogger.e("Failed to migrate user to team: $failure")
            }
    }

    override suspend fun updateTeamId(userId: UserId, teamId: TeamId): Either<StorageFailure, Unit> = wrapStorageRequest {
        userDAO.updateTeamId(userId.toDao(), teamId.value)
    }

    override suspend fun isClientMlsCapable(userId: UserId, clientId: ClientId): Either<StorageFailure, Boolean> = wrapStorageRequest {
        clientDAO.isMLSCapable(userId.toDao(), clientId.value)
    }

    companion object {

        internal const val BATCH_SIZE = 500
    }
}
