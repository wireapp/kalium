package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.functional.Either
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
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface UserRepository {
    suspend fun fetchSelfUser(): Either<CoreFailure, Unit>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun getSelfUser(): Flow<SelfUser>
}

class UserDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfApi: SelfApi,
    private val userApi: UserDetailsApi,
    private val idMapper: IdMapper,
    private val userMapper: UserMapper
) : UserRepository {

    override suspend fun fetchSelfUser(): Either<CoreFailure, Unit> {
        val selfInfoResponse = selfApi.getSelfInfo()

        return if (!selfInfoResponse.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            val user = userMapper.fromApiModelToDaoModel(selfInfoResponse.value)
            userDAO.insertUser(user)
            metadataDAO.insertValue(Json.encodeToString(user.id), SELF_USER_ID_KEY)
            Either.Right(Unit)
        }
    }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            idMapper.toApiModel(idMapper.fromDaoModel(userEntry.id))
        }

        val usersRequestResult = userApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids))
        if (!usersRequestResult.isSuccessful()) {
            usersRequestResult.kException.printStackTrace()
            return Either.Left(CoreFailure.ServerMiscommunication)
        }

        val usersToBePersisted = usersRequestResult.value.map(userMapper::fromApiModelToDaoModel)
        userDAO.insertUsers(usersToBePersisted)
        return Either.Right(Unit)
    }

    override suspend fun getSelfUser(): Flow<SelfUser> {
        return metadataDAO.valueByKey(SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedID = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModel)
        }
    }

    companion object {
        const val SELF_USER_ID_KEY = "selfUserID"

    }
}
