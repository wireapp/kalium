package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.api.user.self.ChangeHandleRequest
import com.wire.kalium.network.api.user.self.SelfApi
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

// FIXME: missing unit test
interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun getSelfUser(): Flow<SelfUser>
    suspend fun getSelfUserId(): QualifiedID
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, SelfUser>
    suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit>
    suspend fun updateLocalSelfUserHandle(handle: String)
    suspend fun getAllContacts(): List<OtherUser>
    suspend fun getKnownUser(userId: UserId): Flow<OtherUser?>
}

class UserDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfApi: SelfApi,
    private val userDetailsApi: UserDetailsApi,
    private val assetRepository: AssetRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper()
) : UserRepository {

    override suspend fun getSelfUserId(): QualifiedID {
        return idMapper.fromDaoModel(_getSelfUserId())
    }

    private suspend fun _getSelfUserId(): QualifiedIDEntity {
        val encodedValue = metadataDAO.valueByKey(SELF_USER_ID_KEY).firstOrNull()
        return encodedValue?.let { Json.decodeFromString<QualifiedIDEntity>(it) }
            ?: run { throw IllegalStateException() }
    }

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = suspending {
        wrapApiRequest { selfApi.getSelfInfo() }
            .map { userMapper.fromApiModelToDaoModel(it).copy(connectionStatus = UserEntity.ConnectionState.ACCEPTED) }
            .flatMap { userEntity ->
                assetRepository.downloadUsersPictureAssets(listOf(userEntity.previewAssetId, userEntity.completeAssetId))
                userDAO.insertUser(userEntity)
                metadataDAO.insertValue(Json.encodeToString(userEntity.id), SELF_USER_ID_KEY)
                Either.Right(Unit)
            }
    }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            idMapper.fromDaoModel(userEntry.id)
        }
        return fetchUsersByIds(ids.toSet())
    }

    override suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit> {
        return suspending {
            wrapApiRequest {
                userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids.map(idMapper::toApiModel)))
            }.flatMap {
                // TODO: handle storage error
                userDAO.insertUsers(it.map(userMapper::fromApiModelToDaoModel))
                Either.Right(Unit)
            }
        }
    }

    override suspend fun getSelfUser(): Flow<SelfUser> {
        // TODO: handle storage error
        return metadataDAO.valueByKey(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModelToSelfUser)
        }
    }

    // FIXME: user info can be updated with null, null and null
    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, SelfUser> {
        val user = getSelfUser().firstOrNull() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))
        val updateRequest = userMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        return suspending {
            wrapApiRequest { selfApi.updateSelf(updateRequest) }
                .map { userMapper.fromUpdateRequestToDaoModel(user, updateRequest) }
                .flatMap {
                    // TODO: handle storage error
                    userDAO.updateUser(it)
                    Either.Right(userMapper.fromDaoModelToSelfUser(it))
                }
        }
    }

    override suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit> = suspending {
        wrapApiRequest {
            selfApi.changeHandle(ChangeHandleRequest(handle))
        }
    }

    override suspend fun updateLocalSelfUserHandle(handle: String) =
        userDAO.updateUserHandle(_getSelfUserId(), handle)

    override suspend fun getAllContacts() {
        val selfUserId = getSelfUserId()

        userDAO.getAllUsersByConnectionStatus(connectionState = UserEntity.ConnectionState.ACCEPTED)
            .filter { it.id.value != selfUserId.value }
            .map { userEntity -> publicUserMapper.fromDaoModelToPublicUser(userEntity) }
    }

    override suspend fun getKnownUser(userId: UserId) =
        userDAO.getUserByQualifiedID(qualifiedID = idMapper.toDaoModel(userId))
            .map { userEntity -> userEntity?.let { publicUserMapper.fromDaoModelToPublicUser(userEntity) } }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
