package com.wire.kalium.logic.data.user.type

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.UserTypeEntity

abstract class DomainUserTypeMapper(
    userTypeConverter: UserTypeConverter<UserType>
) : UserTypeMapper<UserType>(userTypeConverter) {
    abstract fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserType
}

class DomainUserTypeMapperImpl : DomainUserTypeMapper(DomainUserTypeConverter()) {

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
    userTypeConverter: UserTypeConverter<T>
) : UserTypeConverter<T> by userTypeConverter {

    @Suppress("ReturnCount")
    fun fromOtherUserTeamAndDomain(
        otherUserDomain: String,
        selfUserTeamId: String?,
        otherUserTeamId: String?
    ): T {
        if (isUsingWireCloudBackEnd(otherUserDomain)) {
            if (areNotInTheSameTeam(otherUserTeamId, selfUserTeamId)) {
                //delegate
                return guest
            }
        } else {
            if (areNotInTheSameTeam(otherUserTeamId, selfUserTeamId)) {
                //delegate
                return federated
            }
        }

        //delegate
        return internal
    }

    private fun isUsingWireCloudBackEnd(domain: String): Boolean =
        domain.contains(QualifiedID.WIRE_PRODUCTION_DOMAIN)

    // if either self user has no team or other user,
    // does not make sense to compare them and we return false as of they are not on the same team
    private fun areNotInTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        !(otherUserTeamId != null && selfUserTeamId != null) || (otherUserTeamId != selfUserTeamId)
}

abstract class UserEntityTypeMapper : UserTypeMapper<UserTypeEntity>(EntityUserTypeConverter())

class UserEntityTypeMapperImpl : UserEntityTypeMapper()

