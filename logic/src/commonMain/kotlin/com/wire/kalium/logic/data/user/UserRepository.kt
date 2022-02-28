package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.MetadataDAO
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
import com.wire.kalium.persistence.dao.QualifiedID as QualifiedIDEntity

interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(ids: Set<QualifiedID>): Either<CoreFailure, Unit>
    suspend fun getSelfUser(): Flow<SelfUser>
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, Unit>
}

class UserDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfApi: SelfApi,
    private val userApi: UserDetailsApi,
    private val idMapper: IdMapper,
    private val userMapper: UserMapper,
    private val assetRepository: AssetRepository
) : UserRepository {

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> {
        val selfInfoResponse = selfApi.getSelfInfo()

        return if (!selfInfoResponse.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            val user = userMapper.fromApiModelToDaoModel(selfInfoResponse.value)
            // Save (in case there is no data) a reference to the asset id (profile picture)
            assetRepository.saveUserPictureAsset(listOf(user.previewAssetId, user.completeAssetId))
            userDAO.insertUser(user)
            metadataDAO.insertValue(Json.encodeToString(user.id), SELF_USER_ID_KEY)
            Either.Right(Unit)
        }
    }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            idMapper.fromDaoModel(userEntry.id)
        }

        return fetchUsersByIds(ids.toSet())
    }

    override suspend fun fetchUsersByIds(ids: Set<QualifiedID>): Either<CoreFailure, Unit> {
        val mappedIds = ids.map { idMapper.toApiModel(it) }

        val usersRequestResult = userApi.getMultipleUsers(ListUserRequest.qualifiedIds(mappedIds))

        if (!usersRequestResult.isSuccessful()) {
            usersRequestResult.kException.printStackTrace()
            return Either.Left(CoreFailure.ServerMiscommunication)
        }
        val usersToBePersisted = usersRequestResult.value.map(userMapper::fromApiModelToDaoModel)
        // Save (in case there is no data) a reference to the asset id (profile picture)
        assetRepository.saveUserPictureAsset(mapAssetsForUsersToBePersisted(usersToBePersisted))
        userDAO.insertUsers(usersToBePersisted)

        // TODO Wrap DB calls to catch exceptions and return `Either.Left` when exceptions occur
        return Either.Right(Unit)
    }
    
    private fun mapAssetsForUsersToBePersisted(usersToBePersisted: List<UserEntity>): List<UserAssetId> {
        val assetsId = mutableListOf<UserAssetId>()
        usersToBePersisted.map {
            assetsId.add(it.completeAssetId)
            assetsId.add(it.previewAssetId)
        }
        return assetsId
    }

    override suspend fun getSelfUser(): Flow<SelfUser> {
        return metadataDAO.valueByKey(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModel)
        }
    }

    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, Unit> {
        val user = getSelfUser().firstOrNull() ?: return Either.Left(CoreFailure.ServerMiscommunication) // TODO: replace for a DB error

        val updateRequest = userMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        val updatedSelf = selfApi.updateSelf(updateRequest)

        return if (updatedSelf.isSuccessful()) {
            userDAO.updateUser(userMapper.fromUpdateRequestToDaoModel(user, updateRequest))
            Either.Right(Unit)
        } else {
            Either.Left(CoreFailure.ServerMiscommunication)
        }
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
