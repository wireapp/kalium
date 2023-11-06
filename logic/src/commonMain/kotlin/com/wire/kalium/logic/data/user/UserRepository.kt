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

package com.wire.kalium.logic.data.user

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.isFederated
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.network.api.base.model.isTeamMember
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

@Suppress("TooManyFunctions")
interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>

    /**
     * Fetches user information for all of users id stored in the DB
     */
    suspend fun fetchAllOtherUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(qualifiedUserIdList: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun observeSelfUser(): Flow<SelfUser>
    suspend fun observeSelfUserWithTeam(): Flow<Pair<SelfUser, Team?>>
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, Unit>
    suspend fun getSelfUser(): SelfUser?
    suspend fun observeAllKnownUsers(): Flow<Either<StorageFailure, List<OtherUser>>>
    suspend fun getKnownUser(userId: UserId): Flow<OtherUser?>
    suspend fun getKnownUserMinimized(userId: UserId): OtherUserMinimized?
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
    suspend fun removeUser(userId: UserId): Either<CoreFailure, Unit>

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
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class UserDataSource internal constructor(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val clientDAO: ClientDAO,
    private val selfApi: SelfApi,
    private val userDetailsApi: UserDetailsApi,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val teamMapper: TeamMapper = MapperProvider.teamMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val userTypeEntityMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
) : UserRepository {

    /**
     * In case of federated users, we need to refresh their info every time.
     * Since the current backend implementation at wire does not emit user events across backends.
     *
     * This is an in-memory cache, to help avoid unnecessary requests in a time window.
     */
    private val federatedUsersExpirationCache = ConcurrentMutableMap<UserId, Instant>()

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = wrapApiRequest { selfApi.getSelfInfo() }
        .flatMap { userDTO ->
            if (userDTO.deleted == true) {
                Either.Left(SelfUserDeleted)
            } else {
                updateSelfUserProviderAccountInfo(userDTO)
                    .map { userMapper.fromSelfUserDtoToUserEntity(userDTO).copy(connectionStatus = ConnectionEntity.State.ACCEPTED) }
                    .flatMap { userEntity ->
                        wrapStorageRequest { userDAO.upsertUser(userEntity) }
                            .flatMap {
                                wrapStorageRequest { metadataDAO.insertValue(Json.encodeToString(userEntity.id), SELF_USER_ID_KEY) }
                            }
                    }
            }
        }

    private suspend fun updateSelfUserProviderAccountInfo(userDTO: SelfUserDTO): Either<StorageFailure, Unit> =
        sessionRepository.updateSsoIdAndScimInfo(userDTO.id.toModel(), idMapper.toSsoId(userDTO.ssoID), userDTO.managedByDTO)

    override suspend fun getKnownUser(userId: UserId): Flow<OtherUser?> =
        userDAO.observeUserDetailsByQualifiedID(qualifiedID = userId.toDao())
            .map { userEntity ->
                userEntity?.let { userMapper.fromUserDetailsEntityToOtherUser(userEntity) }
            }.onEach { otherUser ->
                processFederatedUserRefresh(userId, otherUser)
            }

    override suspend fun getUsersWithOneOnOneConversation(): List<OtherUser> {
        return userDAO.getUsersWithOneOnOneConversation()
            .map(userMapper::fromUserEntityToOtherUser)
    }

    /**
     * Only in case of federated users and if it's expired or not cached, we fetch and refresh the user info.
     */
    private suspend fun processFederatedUserRefresh(userId: UserId, otherUser: OtherUser?) {
        if (otherUser != null && otherUser.userType.isFederated()
            && federatedUsersExpirationCache[userId]?.let { DateTimeUtil.currentInstant() > it } != false
        ) {
            fetchUserInfo(userId).also {
                kaliumLogger.d("Federated user, refreshing user info from API after $FEDERATED_USER_TTL")
            }
            federatedUsersExpirationCache[userId] = DateTimeUtil.currentInstant().plus(FEDERATED_USER_TTL)
        }
    }

    override suspend fun fetchAllOtherUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.allOtherUsersId().map(UserIDEntity::toModel).toSet()

        return fetchUsersByIds(ids)
    }

    override suspend fun fetchUserInfo(userId: UserId) =
        wrapApiRequest { userDetailsApi.getUserInfo(userId.toApi()) }
            .flatMap { userProfileDTO -> persistUsers(listOf(userProfileDTO)) }

    @Suppress("MagicNumber")
    override suspend fun fetchUsersByIds(qualifiedUserIdList: Set<UserId>): Either<CoreFailure, Unit> =
        if (qualifiedUserIdList.isEmpty()) {
            Either.Right(Unit)
        } else {
            qualifiedUserIdList
                .chunked(BATCH_SIZE)
                .foldToEitherWhileRight(ListUsersDTO(emptyList(), emptyList())) { chunk, acc ->
                    wrapApiRequest {
                        kaliumLogger.d("Fetching ${chunk.size} users")
                        userDetailsApi.getMultipleUsers(
                            ListUserRequest.qualifiedIds(chunk.map { userId -> userId.toApi() })
                        )
                    }.map {
                        kaliumLogger.d("Found ${it.usersFound.size} users and ${it.usersFailed.size} failed users")
                        acc.copy(
                            usersFound = (acc.usersFound + it.usersFound).distinct(),
                            usersFailed = (acc.usersFailed + it.usersFailed).distinct(),
                        )
                    }
                }
                .flatMap { listUserProfileDTO ->
                    if (listUserProfileDTO.usersFailed.isNotEmpty()) {
                        kaliumLogger.d("Handling ${listUserProfileDTO.usersFailed.size} failed users")
                        persistIncompleteUsers(listUserProfileDTO.usersFailed)
                    }
                    persistUsers(listUserProfileDTO.usersFound)
                }
        }

    private suspend fun persistIncompleteUsers(usersFailed: List<NetworkQualifiedId>) = wrapStorageRequest {
        userDAO.insertOrIgnoreUsers(usersFailed.map { userMapper.fromFailedUserToEntity(it) })
    }

    private suspend fun persistUsers(listUserProfileDTO: List<UserProfileDTO>) = wrapStorageRequest {
        val selfUserDomain = selfUserId.domain
        val selfUserTeamId = selfTeamIdProvider().getOrNull()?.value
        val teamMembers = listUserProfileDTO
            .filter { userProfileDTO -> userProfileDTO.isTeamMember(selfUserTeamId, selfUserDomain) }
        val otherUsers = listUserProfileDTO
            .filter { userProfileDTO -> !userProfileDTO.isTeamMember(selfUserTeamId, selfUserDomain) }

        teamMembers.map { userProfileDTO ->
            userMapper.fromUserProfileDtoToUserEntity(
                userProfile = userProfileDTO,
                connectionState = ConnectionEntity.State.ACCEPTED, // this won't be updated, just to avoid a null value
                userTypeEntity = userDAO.observeUserDetailsByQualifiedID(userProfileDTO.id.toDao())
                    .firstOrNull()?.userType ?: UserTypeEntity.STANDARD
            )
        }.let {
            userDAO.upsertUsers(it)
            userDAO.upsertConnectionStatuses(it.associate { it.id to it.connectionStatus })
        }


        userDAO.upsertUsers(
            otherUsers.map { userProfileDTO ->
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
        )
    }

    override suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit> = wrapStorageRequest {
        val qualifiedIDList = ids.map { it.toDao() }
        val knownUsers = userDAO.getUsersDetailsByQualifiedIDList(ids.map { it.toDao() })
        // TODO we should differentiate users with incomplete data not by checking if name isNullOrBlank
        // TODO but add separate property (when federated backend is down)
        qualifiedIDList.filterNot { knownUsers.any { userEntity -> userEntity.id == it && !userEntity.name.isNullOrBlank() } }
    }.flatMap { missingIds ->
        if (missingIds.isEmpty()) Either.Right(Unit)
        else fetchUsersByIds(missingIds.map { it.toModel() }.toSet())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun observeSelfUser(): Flow<SelfUser> {
        return metadataDAO.valueByKeyFlow(SELF_USER_ID_KEY).onEach {
            // If the self user is not in the database, proactively fetch it.
            if (it == null) {
                val logPrefix = "Observing self user before insertion"
                kaliumLogger.w("$logPrefix: Triggering a fetch.")
                fetchSelfUser().fold({ failure ->
                    kaliumLogger.e("""$logPrefix failed: {"failure":"$failure"}""")
                }, {
                    kaliumLogger.i("$logPrefix: Succeeded")
                })
            }
        }.filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.observeUserDetailsByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromUserDetailsEntityToSelfUser)
        }
    }

    @OptIn(FlowPreview::class)
    override suspend fun observeSelfUserWithTeam(): Flow<Pair<SelfUser, Team?>> {
        return metadataDAO.valueByKeyFlow(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserDetailsWithTeamByQualifiedID(selfUserID)
                .filterNotNull()
                .map { (user, team) ->
                    userMapper.fromUserDetailsEntityToSelfUser(user) to team?.let { teamMapper.fromDaoModelToTeam(it) }
                }
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
                    userDAO.updateUser(selfUserId.toDao(), partialUserEntity)
                }
            }.map { }
    }

    // TODO: replace the flow with selfUser and cache it
    override suspend fun getSelfUser(): SelfUser? =
        observeSelfUser().firstOrNull()

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

    override suspend fun getKnownUserMinimized(userId: UserId) = userDAO.getUserMinimizedByQualifiedID(
        qualifiedID = userId.toDao()
    )?.let {
        userMapper.fromUserEntityToOtherUserMinimized(it)
    }

    override suspend fun observeUser(userId: UserId): Flow<User?> =
        userDAO.observeUserDetailsByQualifiedID(qualifiedID = userId.toDao())
            .map { userEntity ->
                // TODO: cache SelfUserId so it's not fetched from DB every single time
                if (userId == selfUserId) {
                    userEntity?.let { userMapper.fromUserDetailsEntityToSelfUser(userEntity) }
                } else {
                    userEntity?.let { userMapper.fromUserDetailsEntityToOtherUser(userEntity) }
                }
            }

    override suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser> =
        wrapApiRequest { userDetailsApi.getUserInfo(userId.toApi()) }.flatMap { userProfileDTO ->
            getSelfUser()?.let { selfUser ->
                Either.Right(
                    userMapper.fromUserProfileDtoToOtherUser(userProfileDTO, selfUser)
                )
            } ?: Either.Left(StorageFailure.DataNotFound)
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

            wrapStorageRequest {
                memberMapper.fromMapOfClientsEntityToRecipients(clientDAO.selectAllClients())
            }.map { allRecipients ->
                val teamRecipients = mutableListOf<Recipient>()
                val otherRecipients = mutableListOf<Recipient>()
                allRecipients.forEach {
                    if (teamMateIds.contains(it.id)) teamRecipients.add(it)
                    else otherRecipients.add(it)
                }
                teamRecipients.toList() to otherRecipients.toList()
            }
        }

    override suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit> = wrapStorageRequest {
        userDAO.updateUser(event.userId.toDao(), userMapper.fromUserUpdateEventToPartialUserEntity(event))
    }.flatMap { updated ->
        if (!updated) {
            Either.Left(StorageFailure.DataNotFound)
        } else {
            Either.Right(Unit)
        }
    }

    override suspend fun removeUser(userId: UserId): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            userDAO.markUserAsDeleted(userId.toDao())
        }
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
        fetchUsersByIds(userIds)
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

    companion object {
        internal const val SELF_USER_ID_KEY = "selfUserID"
        internal val FEDERATED_USER_TTL = 5.minutes
        internal const val BATCH_SIZE = 500
    }
}
