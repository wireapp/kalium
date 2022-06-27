package com.wire.kalium.logic.data.user.type

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.UserTypeEntity


class UserEntityTypeMapperImpl : UserEntityTypeMapper {

    override val guest: UserTypeEntity
        get() = UserTypeEntity.GUEST
    override val federated: UserTypeEntity
        get() = UserTypeEntity.FEDERATED
    override val external: UserTypeEntity
        get() = UserTypeEntity.EXTERNAL
    override val internal: UserTypeEntity
        get() = UserTypeEntity.INTERNAL

}

class DomainUserTypeMapperImpl : DomainUserTypeMapper {

    override val guest: UserType
        get() = UserType.GUEST
    override val federated: UserType
        get() = UserType.FEDERATED
    override val external: UserType
        get() = UserType.EXTERNAL
    override val internal: UserType
        get() = UserType.INTERNAL

    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserType {
        return when (userTypeEntity) {
            UserTypeEntity.INTERNAL -> internal
            UserTypeEntity.EXTERNAL -> external
            UserTypeEntity.FEDERATED -> federated
            UserTypeEntity.GUEST -> guest
        }
    }

}

interface UserEntityTypeMapper : UserTypeMapper<UserTypeEntity>

interface DomainUserTypeMapper : UserTypeMapper<UserType> {
    fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserType
}

interface UserTypeMapper<T> {

    val guest: T
    val federated: T
    val external: T
    val internal: T

    @Suppress("ReturnCount")
    fun fromOtherUserTeamAndDomain(
        otherUserDomain: String,
        selfUserTeamId: String?,
        otherUserTeamId: String?
    ): T = when {
        isUsingWireCloudBackEnd(otherUserDomain) && areNotInTheSameTeam(otherUserTeamId, selfUserTeamId) -> {
            guest
        }
        areNotInTheSameTeam(otherUserTeamId, selfUserTeamId) -> {
            federated
        }
        else -> internal
    }

    private fun isUsingWireCloudBackEnd(domain: String): Boolean =
        domain.contains(QualifiedID.WIRE_PRODUCTION_DOMAIN)

    // if either self user has no team or other user,
    // does not make sense to compare them and we return false as of they are not on the same team
    private fun areNotInTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        !(otherUserTeamId != null && selfUserTeamId != null) || (otherUserTeamId != selfUserTeamId)

}
