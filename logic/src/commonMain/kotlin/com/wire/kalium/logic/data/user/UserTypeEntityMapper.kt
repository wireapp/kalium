package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.UserTypeEntity

interface UserEntityTypeMapper {
    fun fromOtherUserTeamIdAndIdWithSelfUser(
        otherUserDomain: String,
        otherUserTeamID: String?,
        selfUserTeamID: String?
    ): UserTypeEntity
}

class UserEntityTypeMapperImpl : UserEntityTypeMapper {

    @Suppress("ReturnCount")
    override fun fromOtherUserTeamIdAndIdWithSelfUser(
        otherUserDomain: String,
        otherUserTeamID: String?,
        selfUserTeamID: String?
    ): UserTypeEntity {
        if (isUsingWireCloudBackEnd(otherUserDomain)) {
            if (areNotInTheSameTeam(otherUserTeamID, selfUserTeamID)) {
                return UserTypeEntity.GUEST
            }
        } else {
            if (areNotInTheSameTeam(otherUserTeamID, selfUserTeamID)) {
                return UserTypeEntity.FEDERATED
            }
        }

        return UserTypeEntity.INTERNAL
    }

    private fun isUsingWireCloudBackEnd(domain: String): Boolean =
        domain.contains(QualifiedID.WIRE_PRODUCTION_DOMAIN)

    // if either self user has no team or other user,
    // does not make sense to compare them and we return false as of they are not on the same team
    private fun areNotInTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        !(otherUserTeamId != null && selfUserTeamId != null) || (otherUserTeamId != selfUserTeamId)

}
