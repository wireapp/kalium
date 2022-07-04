package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.UsersQueries
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


//This DAO will be responsible for getting/manipulating the ID corresponding to SelfUser only
// on User TABLE , in the future we could change it to a seperate TABLE, we have a Single responsibility
// here that of SelfUser so, no problems here
class SelfUserDao(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
) {

    suspend fun getSelfUserId(): QualifiedIDEntity {
        val encodedValue = metadataDAO.valueByKey(SELF_USER_ID_KEY).firstOrNull()
        return encodedValue?.let { Json.decodeFromString<QualifiedIDEntity>(it) }
            ?: run { throw IllegalStateException() }
    }

    suspend fun getSelfUserEntity(): UserEntity {
        return userDAO.getUserByQualifiedID(getSelfUserId())
    }

    suspend fun updateSelfUser(userEntity: UserEntity){
        userDAO.updateUser(getSelfUserId(), userEntity )
    }

    ...some other methods on SelfUser UserEntity

}
