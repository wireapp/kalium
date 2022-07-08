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
        otherUserTeamId: String?,
        selfUserDomain: String?
    ): T = when {
        isFromDifferentBackEnd(otherUserDomain, selfUserDomain) -> federated
        isFromTheSameTeam(otherUserTeamId, selfUserTeamId) -> internal
        else -> guest
    }

    private fun isFromDifferentBackEnd(otherUserDomain: String, selfDomain: String?): Boolean =
        !otherUserDomain.contains(selfDomain ?: QualifiedID.WIRE_PRODUCTION_DOMAIN)

    private fun isFromTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        otherUserTeamId?.let { it == selfUserTeamId } ?: false

}
