package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.api.user.self.ChangeHandleRequest
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// TODO(testing): missing unit test
@Suppress("TooManyFunctions")
interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun observeSelfUser(): Flow<SelfUser>
    suspend fun getSelfUserId(): QualifiedID
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, SelfUser>
    suspend fun getSelfUser(): SelfUser?
    suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit>
    suspend fun updateLocalSelfUserHandle(handle: String)
    suspend fun getAllKnownUsers(): Either<StorageFailure, List<OtherUser>>
    suspend fun getKnownUser(userId: UserId): Flow<OtherUser?>
    suspend fun observeUser(userId: UserId): Flow<User?>
    suspend fun userById(userId: UserId): Either<CoreFailure, OtherUser>
    suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus)
    suspend fun getAllKnownUsersNotInConversation(conversationId: ConversationId): Either<StorageFailure, List<OtherUser>>
}

@Suppress("LongParameterList", "TooManyFunctions")
class UserDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfApi: SelfApi,
    private val userDetailsApi: UserDetailsApi,
    private val assetRepository: AssetRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val userTypeEntityMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper()
) : UserRepository {

    override suspend fun getSelfUserId(): QualifiedID {
        return idMapper.fromDaoModel(getSelfUserIDEntity())
    }

    private suspend fun getSelfUserIDEntity(): QualifiedIDEntity {
        val encodedValue = metadataDAO.valueByKey(SELF_USER_ID_KEY).firstOrNull()
        return encodedValue?.let { Json.decodeFromString<QualifiedIDEntity>(it) }
            ?: run { throw IllegalStateException() }
    }

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = wrapApiRequest { selfApi.getSelfInfo() }
        .map { userMapper.fromApiSelfModelToDaoModel(it).copy(connectionStatus = ConnectionEntity.State.ACCEPTED) }
        .flatMap { userEntity ->
            assetRepository.downloadUsersPictureAssets(getQualifiedUserAssetId(userEntity))
            userDAO.insertUser(userEntity)
            metadataDAO.insertValue(Json.encodeToString(userEntity.id), SELF_USER_ID_KEY)
            Either.Right(Unit)
        }

    private fun getQualifiedUserAssetId(userEntity: UserEntity): List<UserAssetId?> {
        return mutableListOf<UserAssetId?>().also {
            it.add(userEntity.previewAssetId?.let { asset -> idMapper.fromDaoModel(asset) })
            it.add(userEntity.completeAssetId?.let { asset -> idMapper.fromDaoModel(asset) })
        }
    }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            idMapper.fromDaoModel(userEntry.id)
        }
        return fetchUsersByIds(ids.toSet())
    }

    override suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit> =
        wrapApiRequest {
            userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids.map(idMapper::toApiModel)))
        }.flatMap {
            wrapStorageRequest {
                val selfUser = getSelfUser()
                userDAO.upsertUsers(
                    it.map { userProfileDTO ->
                        userMapper.fromApiModelWithUserTypeEntityToDaoModel(
                            userProfileDTO = userProfileDTO,
                            userTypeEntity = userTypeEntityMapper.fromOtherUserTeamAndDomain(
                                otherUserDomain = userProfileDTO.id.domain,
                                selfUserTeamId = selfUser?.teamId?.value,
                                otherUserTeamId = userProfileDTO.teamId,
                                selfUserDomain = selfUser?.id?.domain
                            )
                        )
                    }
                )
            }
        }

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
        return metadataDAO.valueByKey(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
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
                    userDAO.updateSelfUser(userEntity)
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
        userDAO.updateUserHandle(getSelfUserIDEntity(), handle)

    override suspend fun getAllKnownUsers(): Either<StorageFailure, List<OtherUser>> {
        return wrapStorageRequest {
            val selfUserId = getSelfUserIDEntity()

            userDAO.getAllUsersByConnectionStatus(connectionState = ConnectionEntity.State.ACCEPTED)
                .filter { it.id != selfUserId }
                .map { userEntity -> publicUserMapper.fromDaoModelToPublicUser(userEntity) }
        }
    }

    override suspend fun getKnownUser(userId: UserId): Flow<OtherUser?> =
        userDAO.getUserByQualifiedID(qualifiedID = idMapper.toDaoModel(userId))
            .map { userEntity -> userEntity?.let { publicUserMapper.fromDaoModelToPublicUser(userEntity) } }

    override suspend fun observeUser(userId: UserId): Flow<User?> =
        userDAO.getUserByQualifiedID(qualifiedID = idMapper.toDaoModel(userId))
            .map { userEntity ->
                // TODO: cache SelfUserId so it's not fetched from DB every single time
                if (userId == getSelfUserId()) {
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
                userType = userTypeMapper.fromOtherUserTeamAndDomain(
                    otherUserDomain = userProfile.id.domain,
                    selfUserTeamId = selfUser?.teamId?.value,
                    otherUserTeamId = userProfile.teamId,
                    selfUserDomain = selfUser?.id?.domain
                )
            )
        }

    override suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(getSelfUserIDEntity(), availabilityStatusMapper.fromModelAvailabilityStatusToDao(status))
    }

    override suspend fun getAllKnownUsersNotInConversation(conversationId: ConversationId): Either<StorageFailure, List<OtherUser>> {
        return wrapStorageRequest {
            userDAO.getUsersNotInConversation(idMapper.toDaoModel(conversationId))
                .map { publicUserMapper.fromDaoModelToPublicUser(it) }
        }
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
