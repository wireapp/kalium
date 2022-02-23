package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
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
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun getSelfUser(): Flow<SelfUser>
    suspend fun updateSelfUser(newName: String? = null, newAccent: Int? = null, newAssetId: String? = null): Either<CoreFailure, Unit>
}

class UserDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfApi: SelfApi,
    private val userApi: UserDetailsApi,
    private val idMapper: IdMapper,
    private val userMapper: UserMapper
) : UserRepository {

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> = suspending {
        wrapApiRequest { selfApi.getSelfInfo() }.map {
            userMapper.fromApiModelToDaoModel(it)
        }.coFold({
            return@coFold Either.Left(it)
        }, { user ->
            userDAO.insertUser(user)
            metadataDAO.insertValue(Json.encodeToString(user.id), SELF_USER_ID_KEY)
            return@coFold Either.Right(Unit)

        })
    }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            idMapper.toApiModel(idMapper.fromDaoModel(userEntry.id))
        }
        return suspending {
            wrapApiRequest {
                userApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids))
            }.coFold({
                Either.Left(it)
            }, {
                userDAO.insertUsers(it.map(userMapper::fromApiModelToDaoModel))
                Either.Right(Unit)
            })
        }
    }

    override suspend fun getSelfUser(): Flow<SelfUser> {
        return metadataDAO.valueByKey(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedID = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModel)
        }
    }

    override suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, Unit> {
        val user = getSelfUser().firstOrNull() ?: return Either.Left(CoreFailure.Unknown(NullPointerException())) // TODO: replace for a DB error

        val updateRequest = userMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        val updatedSelf = selfApi.updateSelf(updateRequest)

        return if (updatedSelf.isSuccessful()) {
            userDAO.updateUser(userMapper.fromUpdateRequestToDaoModel(user, updateRequest))
            Either.Right(Unit)
        } else {
            Either.Left(CoreFailure.Unknown(NullPointerException())) // TODO: replace for a DB error
        }
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"
    }
}
