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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.isFederated
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.self.ChangeHandleRequest
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserProfileDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.util.DateTimeUtil
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

@Suppress("TooManyFunctions")
internal interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(qualifiedUserIdList: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun observeSelfUser(): Flow<SelfUser>
    suspend fun observeSelfUserWithTeam(): Flow<Pair<SelfUser, Team?>>
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, SelfUser>
    suspend fun getSelfUser(): SelfUser?
    suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit>
    suspend fun updateSelfDisplayName(displayName: String): Either<CoreFailure, Unit>
    suspend fun updateLocalSelfUserHandle(handle: String)
    fun observeAllKnownUsers(): Flow<Either<StorageFailure, List<OtherUser>>>
    suspend fun getKnownUser(userId: UserId): Flow<OtherUser?>
    suspend fun getKnownUserMinimized(userId: UserId): OtherUserMinimized?
    suspend fun observeUser(userId: UserId): Flow<User?>
    suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser>
    suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus)
    suspend fun updateOtherUserAvailabilityStatus(userId: UserId, status: UserAvailabilityStatus)
    fun observeAllKnownUsersNotInConversation(conversationId: ConversationId): Flow<Either<StorageFailure, List<OtherUser>>>
    suspend fun getUsersFromTeam(teamId: TeamId): Either<StorageFailure, List<OtherUser>>
    suspend fun getTeamRecipients(teamId: TeamId): Either<CoreFailure, List<Recipient>>
    suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit>
    suspend fun removeUser(userId: UserId): Either<CoreFailure, Unit>
    suspend fun insertUsersIfUnknown(users: List<User>): Either<StorageFailure, Unit>
    suspend fun fetchUserInfo(userId: UserId): Either<CoreFailure, Unit>
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
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val teamMapper: TeamMapper = MapperProvider.teamMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val userTypeEntityMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
) : UserRepository {

    /**
     * In case of federated users, we need to refresh their info every time.
     * Since the current backend implementation at wire does not emit user events across backends.
     *
     * This is an in-memory cache, to help avoid unnecessary requests in a time window.
     */
    private val federatedUsersExpirationCache = ConcurrentMap<UserId, Instant>()

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = wrapApiRequest { selfApi.getSelfInfo() }
        .flatMap { userDTO ->
            if (userDTO.deleted == true) {
                Either.Left(SelfUserDeleted)
            } else {
                updateSelfUserProviderAccountInfo(userDTO)
                    .map { userMapper.fromApiSelfModelToDaoModel(userDTO).copy(connectionStatus = ConnectionEntity.State.ACCEPTED) }
                    .flatMap { userEntity ->
                        wrapStorageRequest { userDAO.insertUser(userEntity) }
                            .flatMap {
                                wrapStorageRequest { metadataDAO.insertValue(Json.encodeToString(userEntity.id), SELF_USER_ID_KEY) }
                            }
                    }
            }
        }

    private suspend fun updateSelfUserProviderAccountInfo(userDTO: UserDTO): Either<StorageFailure, Unit> =
        sessionRepository.updateSsoIdAndScimInfo(userDTO.id.toModel(), idMapper.toSsoId(userDTO.ssoID), userDTO.managedByDTO)

    override suspend fun getKnownUser(userId: UserId): Flow<OtherUser?> =
        userDAO.getUserByQualifiedID(qualifiedID = userId.toDao())
            .map { userEntity ->
                userEntity?.let { publicUserMapper.fromDaoModelToPublicUser(userEntity) }
            }.onEach { otherUser ->
                processFederatedUserRefresh(userId, otherUser)
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

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            userEntry.id.toModel()
        }
        return fetchUsersByIds(ids.toSet())
    }

    override suspend fun fetchUsersByIds(qualifiedUserIdList: Set<UserId>): Either<CoreFailure, Unit> {
        val selfUserDomain = selfUserId.domain
        qualifiedUserIdList.groupBy { it.domain }
            .filter { it.value.isNotEmpty() }
            .map { (domain: String, usersOnDomain: List<UserId>) ->
                when (selfUserDomain == domain) {
                    true -> fetchMultipleUsers(usersOnDomain)
                    false -> {
                        usersOnDomain.forEach { userId ->
                            fetchUserInfo(userId).fold({
                                kaliumLogger.w("Ignoring external users details")
                            }) { kaliumLogger.d("External users details saved") }
                        }
                        Either.Right(Unit)
                    }
                }
            }

        return Either.Right(Unit)
    }

    private suspend fun fetchMultipleUsers(qualifiedUsersOnSameDomainList: List<UserId>) = wrapApiRequest {
        userDetailsApi.getMultipleUsers(
            ListUserRequest.qualifiedIds(qualifiedUsersOnSameDomainList.map { userId -> userId.toApi() })
        )
    }.flatMap { listUserProfileDTO -> persistUsers(listUserProfileDTO) }

    override suspend fun fetchUserInfo(userId: UserId) =
        wrapApiRequest { userDetailsApi.getUserInfo(userId.toApi()) }
            .flatMap { userProfileDTO -> persistUsers(listOf(userProfileDTO)) }

    private suspend fun persistUsers(listUserProfileDTO: List<UserProfileDTO>) = wrapStorageRequest {
        val selfUserDomain = selfUserId.domain
        val selfUserTeamId = selfTeamIdProvider().getOrNull()?.value
        val teamMembers = listUserProfileDTO
            .filter { userProfileDTO -> isTeamMember(selfUserTeamId, userProfileDTO, selfUserDomain) }
        val otherUsers = listUserProfileDTO
            .filter { userProfileDTO -> !isTeamMember(selfUserTeamId, userProfileDTO, selfUserDomain) }
        userDAO.upsertTeamMembers(
            teamMembers.map { userProfileDTO ->
                userMapper.fromApiModelWithUserTypeEntityToDaoModel(
                    userProfileDTO = userProfileDTO,
                    userTypeEntity = null
                )
            }
        )

        userDAO.upsertUsers(
            otherUsers.map { userProfileDTO ->
                userMapper.fromApiModelWithUserTypeEntityToDaoModel(
                    userProfileDTO = userProfileDTO,
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

    private fun isTeamMember(
        selfUserTeamId: String?,
        userProfileDTO: UserProfileDTO,
        selfUserDomain: String?,
    ) = (selfUserTeamId != null &&
            userProfileDTO.teamId == selfUserTeamId &&
            userProfileDTO.id.domain == selfUserDomain)

    override suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit> = wrapStorageRequest {
        val qualifiedIDList = ids.map { it.toDao() }
        val knownUsers = userDAO.getUsersByQualifiedIDList(ids.map { it.toDao() })
        // TODO we should differentiate users with incomplete data not by checking if name isNullOrBlank
        // TODO but add separate property (when federated backend is down)
        qualifiedIDList.filterNot { knownUsers.any { userEntity -> userEntity.id == it && !userEntity.name.isNullOrBlank() } }
    }.flatMap { missingIds ->
        if (missingIds.isEmpty()) Either.Right(Unit)
        else fetchUsersByIds(missingIds.map { it.toModel() }.toSet())
    }

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
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModelToSelfUser)
        }
    }

    @OptIn(FlowPreview::class)
    override suspend fun observeSelfUserWithTeam(): Flow<Pair<SelfUser, Team?>> {
        return metadataDAO.valueByKeyFlow(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserWithTeamByQualifiedID(selfUserID)
                .filterNotNull()
                .map { (user, team) ->
                    userMapper.fromDaoModelToSelfUser(user) to team?.let { teamMapper.fromDaoModelToTeam(it) }
                }
        }
    }

    @Deprecated(
        message = "Create a dedicated function to update the corresponding user property, instead of updating the whole user",
        replaceWith = ReplaceWith("eg: updateSelfDisplayName(displayName: String)")
    )
    // FIXME(refactor): create a dedicated function to update avatar, as this is the only usage of this function.
    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, SelfUser> {
        val user = getSelfUser() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))
        val updateRequest = userMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        return wrapApiRequest { selfApi.updateSelf(updateRequest) }
            .map { userMapper.fromUpdateRequestToDaoModel(user, updateRequest) }
            .flatMap { userEntity ->
                wrapStorageRequest {
                    userDAO.updateUser(userEntity)
                }.map { userMapper.fromDaoModelToSelfUser(userEntity) }
            }
    }

    // TODO: replace the flow with selfUser and cache it
    override suspend fun getSelfUser(): SelfUser? =
        observeSelfUser().firstOrNull()

    override suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit> = wrapApiRequest {
        selfApi.changeHandle(ChangeHandleRequest(handle))
    }

    override suspend fun updateSelfDisplayName(displayName: String): Either<CoreFailure, Unit> = wrapApiRequest {
        selfApi.updateSelf(UserUpdateRequest(displayName, null, null))
    }.flatMap {
        wrapStorageRequest {
            userDAO.updateUserDisplayName(selfUserId.toDao(), displayName)
        }
    }

    override suspend fun updateLocalSelfUserHandle(handle: String) =
        userDAO.updateUserHandle(selfUserId.toDao(), handle)

    override fun observeAllKnownUsers(): Flow<Either<StorageFailure, List<OtherUser>>> {
        val selfUserId = selfUserId.toDao()
        return userDAO.observeAllUsersByConnectionStatus(connectionState = ConnectionEntity.State.ACCEPTED)
            .wrapStorageRequest()
            .mapRight { users ->
                users
                    .filter { it.id != selfUserId && !it.deleted }
                    .map { userEntity -> publicUserMapper.fromDaoModelToPublicUser(userEntity) }
            }
    }

    override suspend fun getKnownUserMinimized(userId: UserId) = userDAO.getUserMinimizedByQualifiedID(
        qualifiedID = userId.toDao()
    )?.let {
        publicUserMapper.fromDaoModelToPublicUserMinimized(it)
    }

    override suspend fun observeUser(userId: UserId): Flow<User?> =
        userDAO.getUserByQualifiedID(qualifiedID = userId.toDao())
            .map { userEntity ->
                // TODO: cache SelfUserId so it's not fetched from DB every single time
                if (userId == selfUserId) {
                    userEntity?.let { userMapper.fromDaoModelToSelfUser(userEntity) }
                } else {
                    userEntity?.let { publicUserMapper.fromDaoModelToPublicUser(userEntity) }
                }
            }

    override suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser> =
        wrapApiRequest { userDetailsApi.getUserInfo(userId.toApi()) }.flatMap { userProfileDTO ->
            getSelfUser()?.teamId.let { selfTeamId ->
                Either.Right(
                    publicUserMapper.fromUserDetailResponseWithUsertype(
                        userDetailResponse = userProfileDTO,
                        userType = userTypeMapper.fromTeamAndDomain(
                            otherUserDomain = userProfileDTO.id.domain,
                            selfUserTeamId = selfTeamId?.value,
                            otherUserTeamId = userProfileDTO.teamId,
                            selfUserDomain = selfUserId.domain,
                            isService = userProfileDTO.service != null
                        )
                    )
                )
            }
        }

    override suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(
            selfUserId.toDao(),
            availabilityStatusMapper.fromModelAvailabilityStatusToDao(status)
        )
    }

    override suspend fun updateOtherUserAvailabilityStatus(userId: UserId, status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(userId.toDao(), availabilityStatusMapper.fromModelAvailabilityStatusToDao(status))
    }

    override fun observeAllKnownUsersNotInConversation(
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, List<OtherUser>>> {
        return userDAO.observeUsersNotInConversation(conversationId.toDao())
            .wrapStorageRequest()
            .mapRight { users ->
                users
                    .filter { !it.deleted }
                    .map { publicUserMapper.fromDaoModelToPublicUser(it) }
            }
    }

    override suspend fun getUsersFromTeam(teamId: TeamId): Either<StorageFailure, List<OtherUser>> {
        return wrapStorageRequest {
            val selfUserId = selfUserId.toDao()

            userDAO.getAllUsersByTeam(teamId.value)
                .filter { it.id != selfUserId }
                .map(publicUserMapper::fromDaoModelToPublicUser)
        }
    }

    override suspend fun getTeamRecipients(teamId: TeamId): Either<CoreFailure, List<Recipient>> =
        getUsersFromTeam(teamId)
            .map { users ->
                users.associate { user -> user.id to clientDAO.getClientsOfUserByQualifiedID(user.id.toDao()) }
            }
            .map(memberMapper::fromMapOfClientsToRecipients)

    override suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit> = wrapStorageRequest {
        val userId = qualifiedIdMapper.fromStringToQualifiedID(event.userId)
        val user =
            userDAO.getUserByQualifiedID(userId.toDao()).firstOrNull() ?: return Either.Left(StorageFailure.DataNotFound)
        userDAO.updateUser(userMapper.toUpdateDaoFromEvent(event, user))
    }

    override suspend fun removeUser(userId: UserId): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            userDAO.markUserAsDeleted(userId.toDao())
        }
    }

    override suspend fun insertUsersIfUnknown(users: List<User>): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userDAO.insertOrIgnoreUsers(
                users.map { user ->
                    when (user) {
                        is OtherUser -> publicUserMapper.fromPublicUserToDaoModel(user)
                        is SelfUser -> userMapper.fromSelfUserToDaoModel(user)
                    }
                }
            )
        }

    companion object {
        internal const val SELF_USER_ID_KEY = "selfUserID"
        internal val FEDERATED_USER_TTL = 5.minutes
    }
}
