package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.api.user.self.ChangeHandleRequest
import com.wire.kalium.network.api.user.self.SelfApi
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
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, SelfUser>
    suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String): Flow<List<UserEntity>>
    suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit>
}

class UserDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfApi: SelfApi,
    private val userApi: UserDetailsApi,
    private val idMapper: IdMapper,
    private val userMapper: UserMapper,
    private val assetRepository: AssetRepository,
    private val teamRepository: TeamRepository
) : UserRepository {

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = suspending {
        wrapApiRequest { selfApi.getSelfInfo() }.map {
            userMapper.fromApiModelToDaoModel(it)
        }.coFold({
            Either.Left(it)
        }, { user ->
            // Fetching self user team here because SyncSelfUserUseCase is (which is using this function)
            // doesn't have a return value, and this team fetch doesn't make sense adding to GetSelfUserUseCase
            // as we don't want to be fetching the user team everytime we get the user.
            user.team?.let { teamId -> teamRepository.fetchTeamById(teamId = teamId) }
            assetRepository.downloadUsersPictureAssets(listOf(user.previewAssetId, user.completeAssetId))
            // TODO: handle storage error
            userDAO.insertUser(user)
            metadataDAO.insertValue(Json.encodeToString(user.id), SELF_USER_ID_KEY)
            Either.Right(Unit)
        })
    }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        // TODO: handle storage error
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
                // TODO: handle storage error
                userDAO.insertUsers(it.map(userMapper::fromApiModelToDaoModel))
                Either.Right(Unit)
            })
        }
    }

    override suspend fun getSelfUser(): Flow<SelfUser> {
        // TODO: handle storage error
        return metadataDAO.valueByKey(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModel)
        }
    }

    // FIXME: user info can be updated with null, null and null
    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, SelfUser> {
        val user =
            getSelfUser().firstOrNull() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))
        val updateRequest = userMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        return suspending {
            wrapApiRequest { selfApi.updateSelf(updateRequest) }
                .map { userMapper.fromUpdateRequestToDaoModel(user, updateRequest) }
                .flatMap {
                // TODO: handle storage error
                userDAO.updateUser(it)
                Either.Right(userMapper.fromDaoModel(it))
            }
        }
    }


    override suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String) =
        userDAO.getUserByNameOrHandleOrEmail(searchQuery)

    override suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit> = wrapApiRequest {
        selfApi.changeHandle(ChangeHandleRequest(handle))
    }.flatMap {
         TODO("store handle locally")
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
