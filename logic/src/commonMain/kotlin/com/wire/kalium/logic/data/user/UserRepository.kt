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
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.api.user.self.ChangeHandleRequest
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("TooManyFunctions")
internal interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun observeSelfUser(): Flow<SelfUser>
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, SelfUser>
    suspend fun getSelfUser(): SelfUser?
    suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit>
    suspend fun updateLocalSelfUserHandle(handle: String)
    suspend fun getAllKnownUsers(): Either<StorageFailure, List<OtherUser>>
    suspend fun getKnownUser(userId: UserId): Flow<OtherUser?>
    suspend fun getKnownUserMinimized(userId: UserId): OtherUserMinimized?
    suspend fun observeUser(userId: UserId): Flow<User?>
    suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser>
    suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus)
    suspend fun updateOtherUserAvailabilityStatus(userId: UserId, status: UserAvailabilityStatus)
    suspend fun getAllKnownUsersNotInConversation(conversationId: ConversationId): Either<StorageFailure, List<OtherUser>>
    suspend fun getUsersFromTeam(teamId: TeamId): Either<StorageFailure, List<OtherUser>>
    suspend fun getTeamRecipients(teamId: TeamId): Either<CoreFailure, List<Recipient>>
    suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit>
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
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val userTypeEntityMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
) : UserRepository {

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = wrapApiRequest { selfApi.getSelfInfo() }
        .flatMap { userDTO ->
            // update self user SsoId
            sessionRepository.updateSsoId(idMapper.fromApiModel(userDTO.id), idMapper.toSsoId(userDTO.ssoID))
                .map { userMapper.fromApiSelfModelToDaoModel(userDTO).copy(connectionStatus = ConnectionEntity.State.ACCEPTED) }
                .flatMap { userEntity ->
                    wrapStorageRequest { userDAO.insertUser(userEntity) }
                        .flatMap {
                            wrapStorageRequest { metadataDAO.insertValue(Json.encodeToString(userEntity.id), SELF_USER_ID_KEY) }
                        }
                }
        }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            idMapper.fromDaoModel(userEntry.id)
        }
        return fetchUsersByIds(ids.toSet())
    }

    override suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit> {
        val selfUserDomain = selfUserId.domain
        ids.groupBy { it.domain }
            .map {
                val usersOnSameDomain = it.key == selfUserDomain
                if (usersOnSameDomain) {
                    wrapApiRequest {
                        userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids.map(idMapper::toApiModel)))
                    }.flatMap { listUserProfileDTO -> persistUsers(listUserProfileDTO) }
                } else {
                    it.value.forEach { userId ->
                        wrapApiRequest { userDetailsApi.getUserInfo(idMapper.toApiModel(userId)) }
                            .fold(
                                { kaliumLogger.w("Ignoring external users details") },
                                { userProfileDTO -> persistUsers(listOf(userProfileDTO)) }
                            )
                    }
                    Either.Right(Unit)
                }
            }

        return Either.Right(Unit)
    }

    private suspend fun persistUsers(listUserProfileDTO: List<UserProfileDTO>) =
        wrapStorageRequest {
            val selfUser = getSelfUser()
            val selfUserTeamId = selfUser?.teamId?.value
            val teamMembers = listUserProfileDTO
                .filter { userProfileDTO -> isTeamMember(selfUserTeamId, userProfileDTO, selfUser) }
            val otherUsers = listUserProfileDTO
                .filter { userProfileDTO -> !isTeamMember(selfUserTeamId, userProfileDTO, selfUser) }
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
                            selfUserTeamId = selfUser?.teamId?.value,
                            otherUserTeamId = userProfileDTO.teamId,
                            selfUserDomain = selfUser?.id?.domain,
                            isService = userProfileDTO.service != null
                        )
                    )
                }
            )
        }

    private fun isTeamMember(
        selfUserTeamId: String?,
        userProfileDTO: UserProfileDTO,
        selfUser: SelfUser?
    ) = (selfUserTeamId != null &&
            userProfileDTO.teamId == selfUserTeamId &&
            userProfileDTO.id.domain == selfUser?.id?.domain)

    override suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit> = wrapStorageRequest {
        val qualifiedIDList = ids.map(idMapper::toDaoModel)
        val knownUsers = userDAO.getUsersByQualifiedIDList(ids.map(idMapper::toDaoModel))
        qualifiedIDList.filterNot { knownUsers.any { userEntity -> userEntity.id == it } }
    }.flatMap { missingIds ->
        if (missingIds.isEmpty()) Either.Right(Unit)
        else fetchUsersByIds(missingIds.map { idMapper.fromDaoModel(it) }.toSet())
    }

    override suspend fun observeSelfUser(): Flow<SelfUser> {
        // TODO: handle storage error
        return metadataDAO.valueByKeyFlow(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModelToSelfUser)
        }
    }

    // FIXME(refactor): user info can be updated with null, null and null
    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, SelfUser> {
        val user = observeSelfUser().firstOrNull() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))
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

    override suspend fun updateLocalSelfUserHandle(handle: String) =
        userDAO.updateUserHandle(idMapper.toDaoModel(selfUserId), handle)

    override suspend fun getAllKnownUsers(): Either<StorageFailure, List<OtherUser>> {
        return wrapStorageRequest {
            val selfUserId = idMapper.toDaoModel(selfUserId)

            userDAO.getAllUsersByConnectionStatus(connectionState = ConnectionEntity.State.ACCEPTED)
                .filter { it.id != selfUserId }
                .map { userEntity -> publicUserMapper.fromDaoModelToPublicUser(userEntity) }
        }
    }

    override suspend fun getKnownUser(userId: UserId): Flow<OtherUser?> =
        userDAO.getUserByQualifiedID(qualifiedID = idMapper.toDaoModel(userId))
            .map { userEntity -> userEntity?.let { publicUserMapper.fromDaoModelToPublicUser(userEntity) } }

    override suspend fun getKnownUserMinimized(userId: UserId) = userDAO.getUserMinimizedByQualifiedID(
        qualifiedID = idMapper.toDaoModel(userId)
    )?.let {
        publicUserMapper.fromDaoModelToPublicUserMinimized(it)
    }

    override suspend fun observeUser(userId: UserId): Flow<User?> =
        userDAO.getUserByQualifiedID(qualifiedID = idMapper.toDaoModel(userId))
            .map { userEntity ->
                // TODO: cache SelfUserId so it's not fetched from DB every single time
                if (userId == selfUserId) {
                    userEntity?.let { userMapper.fromDaoModelToSelfUser(userEntity) }
                } else {
                    userEntity?.let { publicUserMapper.fromDaoModelToPublicUser(userEntity) }
                }
            }

    override suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser> =
        wrapApiRequest { userDetailsApi.getUserInfo(idMapper.toApiModel(userId)) }.map { userProfile ->
            val selfUser = getSelfUser()
            publicUserMapper.fromUserDetailResponseWithUsertype(
                userDetailResponse = userProfile,
                userType = userTypeMapper.fromTeamAndDomain(
                    otherUserDomain = userProfile.id.domain,
                    selfUserTeamId = selfUser?.teamId?.value,
                    otherUserTeamId = userProfile.teamId,
                    selfUserDomain = selfUser?.id?.domain,
                    isService = userProfile.service != null
                )
            )
        }

    override suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(
            idMapper.toDaoModel(selfUserId),
            availabilityStatusMapper.fromModelAvailabilityStatusToDao(status)
        )
    }

    override suspend fun updateOtherUserAvailabilityStatus(userId: UserId, status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(idMapper.toDaoModel(userId), availabilityStatusMapper.fromModelAvailabilityStatusToDao(status))
    }

    override suspend fun getAllKnownUsersNotInConversation(conversationId: ConversationId): Either<StorageFailure, List<OtherUser>> {
        return wrapStorageRequest {
            userDAO.getUsersNotInConversation(idMapper.toDaoModel(conversationId))
                .map { publicUserMapper.fromDaoModelToPublicUser(it) }
        }
    }

    override suspend fun getUsersFromTeam(teamId: TeamId): Either<StorageFailure, List<OtherUser>> {
        return wrapStorageRequest {
            val selfUserId = idMapper.toDaoModel(selfUserId)

            userDAO.getAllUsersByTeam(teamId.value)
                .filter { it.id != selfUserId }
                .map(publicUserMapper::fromDaoModelToPublicUser)
        }
    }

    override suspend fun getTeamRecipients(teamId: TeamId): Either<CoreFailure, List<Recipient>> =
        getUsersFromTeam(teamId)
            .map { users ->
                users.associate { user -> user.id to clientDAO.getClientsOfUserByQualifiedID(idMapper.toDaoModel(user.id)) }
            }
            .map(memberMapper::fromMapOfClientsToRecipients)

    override suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit> = wrapStorageRequest {
        val userId = qualifiedIdMapper.fromStringToQualifiedID(event.userId)
        val user = userDAO.getUserByQualifiedID(idMapper.toDaoModel(userId)).firstOrNull()
        userDAO.updateUser(userMapper.toUpdateDaoFromEvent(event, user!!))
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
