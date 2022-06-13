package com.wire.kalium.logic.data.user.self

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.mapper.UserEntityMapper
import com.wire.kalium.logic.data.user.mapper.UserUpdateRequestMapper
import com.wire.kalium.logic.data.user.self.mapper.SelfUserMapper
import com.wire.kalium.logic.data.user.self.model.SelfUser
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.self.ChangeHandleRequest
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// TODO(testing): missing unit test
interface SelfUserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun getSelfUser(): Flow<SelfUser>
    suspend fun getSelfUserId(): QualifiedID
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, SelfUser>
    suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit>
    suspend fun updateLocalSelfUserHandle(handle: String)
    suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus)
}

@Suppress("LongParameterList")
class SelfUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfApi: SelfApi,
    private val assetRepository: AssetRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userEntityMapper: UserEntityMapper = MapperProvider.userMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val selfUserMapper: SelfUserMapper = MapperProvider.selfUserMapper(),
    private val userUpdateRequestMapper: UserUpdateRequestMapper = MapperProvider.userUpdateRequestMapper()
) : SelfUserRepository {

    override suspend fun getSelfUserId(): QualifiedID {
        return idMapper.fromDaoModel(getSelfUserIdEntity())
    }

    private suspend fun getSelfUserIdEntity(): QualifiedIDEntity {
        val encodedValue = metadataDAO.valueByKey(SELF_USER_ID_KEY).firstOrNull()

        return encodedValue?.let { Json.decodeFromString<QualifiedIDEntity>(it) }
            ?: run { throw IllegalStateException() }
    }

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = wrapApiRequest { selfApi.getSelfInfo() }
        .map { userEntityMapper.fromUserDTO(it).copy(connectionStatus = ConnectionEntity.State.ACCEPTED) }
        .flatMap { userEntity ->
            assetRepository.downloadUsersPictureAssets(listOf(userEntity.previewAssetId, userEntity.completeAssetId))
            userDAO.insertUser(userEntity)
            metadataDAO.insertValue(Json.encodeToString(userEntity.id), SELF_USER_ID_KEY)
            Either.Right(Unit)
        }

    override suspend fun getSelfUser(): Flow<SelfUser> {
        // TODO: handle storage error
        return userDAO.getUserByQualifiedID(getSelfUserIdEntity())
            .filterNotNull()
            .map(selfUserMapper::fromUserEntity)
    }

    // FIXME(refactor): user info can be updated with null, null and null
    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, SelfUser> {
        val user = getSelfUser().firstOrNull() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))
        val updateRequest = userUpdateRequestMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        return wrapApiRequest { selfApi.updateSelf(updateRequest) }
            .map { userEntityMapper.fromSelfUserWithUserUpdateRequest(user, updateRequest) }
            .flatMap { userEntity ->
                wrapStorageRequest {
                    userDAO.updateSelfUser(userEntity)
                }.map { selfUserMapper.fromUserEntity(userEntity) }
            }
    }

    override suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit> = wrapApiRequest {
        selfApi.changeHandle(ChangeHandleRequest(handle))
    }

    override suspend fun updateLocalSelfUserHandle(handle: String) =
        userDAO.updateUserHandle(getSelfUserIdEntity(), handle)

    override suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus) {
        userDAO.updateUserAvailabilityStatus(getSelfUserIdEntity(), availabilityStatusMapper.fromModelAvailabilityStatusToDao(status))
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
