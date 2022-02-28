package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
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
    suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
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

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = suspending {
        wrapApiRequest { selfApi.getSelfInfo() }.map {
            userMapper.fromApiModelToDaoModel(it)
        }.coFold({
            Either.Left(it)
        }, { user ->
            assetRepository.saveUserPictureAsset(listOf(user.previewAssetId, user.completeAssetId))
            userDAO.insertUser(user)
            metadataDAO.insertValue(Json.encodeToString(user.id), SELF_USER_ID_KEY)
            Either.Right(Unit)
        })
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
                userApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids.map(idMapper::toApiModel)))
            }.coFold({
                Either.Left(it)
            }, {
                val usersToBePersisted = it.map(userMapper::fromApiModelToDaoModel)
                // Save (in case there is no data) a reference to the asset id (profile picture)
                assetRepository.saveUserPictureAsset(mapAssetsForUsersToBePersisted(usersToBePersisted))
                userDAO.insertUsers(usersToBePersisted)
                Either.Right(Unit)
            })
        }
    }

    override suspend fun getSelfUser(): Flow<SelfUser> {
        return metadataDAO.valueByKey(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModel)
        }
    }

    private fun mapAssetsForUsersToBePersisted(usersToBePersisted: List<UserEntity>): List<UserAssetId> {
        val assetsId = mutableListOf<UserAssetId>()
        usersToBePersisted.map {
            assetsId.add(it.completeAssetId)
            assetsId.add(it.previewAssetId)
        }
        return assetsId
    }

    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, Unit> {
        val user =
            getSelfUser().firstOrNull() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))

        val updateRequest = userMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        val updatedSelf = selfApi.updateSelf(updateRequest)

        return if (updatedSelf.isSuccessful()) {
            userDAO.updateUser(userMapper.fromUpdateRequestToDaoModel(user, updateRequest))
            Either.Right(Unit)
        } else {
            Either.Left(CoreFailure.Unknown(IllegalStateException()))
        }
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
