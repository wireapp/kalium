package com.wire.kalium.logic.data.user.type

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

abstract class DomainUserTypeMapper(
    userDAO: UserDAO,
    metadataDAO: MetadataDAO,
    userMapper: UserMapper,
    userTypeConverter: UserTypeConverter<UserType>
) : UserTypeMapper<UserType>(userDAO, metadataDAO, userMapper,userTypeConverter) {
    abstract fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserType
}

class DomainUserTypeMapperImpl(
    userDAO: UserDAO,
    metadataDAO: MetadataDAO,
    userMapper: UserMapper
) : DomainUserTypeMapper(userDAO, metadataDAO, userMapper, DomainUserTypeConverter()) {

    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserType {
        return when (userTypeEntity) {
            UserTypeEntity.INTERNAL -> UserType.INTERNAL
            UserTypeEntity.EXTERNAL -> UserType.EXTERNAL
            UserTypeEntity.FEDERATED -> UserType.FEDERATED
            UserTypeEntity.GUEST -> UserType.GUEST
        }
    }

}

abstract class UserTypeMapper<T>(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val userMapper: UserMapper,
    userTypeConverter: UserTypeConverter<T>
) : UserTypeConverter<T> by userTypeConverter {

    private suspend fun observeSelfUser(): Flow<SelfUser> {
        // TODO: handle storage error
        return metadataDAO.valueByKey(UserDataSource.SELF_USER_ID_KEY).filterNotNull().flatMapMerge { encodedValue ->
            val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)
            userDAO.getUserByQualifiedID(selfUserID)
                .filterNotNull()
                .map(userMapper::fromDaoModelToSelfUser)
        }
    }

    @Suppress("ReturnCount")
    suspend fun fromOtherUserTeamAndDomain(
        otherUserDomain: String,
        otherUserTeamID: String?
    ): T {
        val selfUser = observeSelfUser().firstOrNull()

        return if (selfUser != null) {
            if (isUsingWireCloudBackEnd(otherUserDomain)) {
                if (areNotInTheSameTeam(otherUserTeamID, selfUser.team)) {
                    //delegate
                    return guest
                }
            } else {
                if (areNotInTheSameTeam(otherUserTeamID, selfUser.team)) {
                    //delegate
                    return federated
                }
            }

            //delegate
            return internal
        } else {
            internal
        }
    }

    private fun isUsingWireCloudBackEnd(domain: String): Boolean =
        domain.contains(QualifiedID.WIRE_PRODUCTION_DOMAIN)

    // if either self user has no team or other user,
    // does not make sense to compare them and we return false as of they are not on the same team
    private fun areNotInTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        !(otherUserTeamId != null && selfUserTeamId != null) || (otherUserTeamId != selfUserTeamId)
}

abstract class UserEntityTypeMapper(
    userDAO: UserDAO,
    metadataDAO: MetadataDAO,
    userMapper: UserMapper
) : UserTypeMapper<UserTypeEntity>(userDAO, metadataDAO, userMapper, EntityUserTypeConverter())

class UserEntityTypeMapperImpl(
    userDAO: UserDAO,
    metadataDAO: MetadataDAO,
    userMapper: UserMapper
) : UserEntityTypeMapper(userDAO, metadataDAO, userMapper)

