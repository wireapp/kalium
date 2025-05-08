/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.user.type

import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.Mockable

class UserEntityTypeMapperImpl : UserEntityTypeMapper {

    override val guest: UserTypeEntity
        get() = UserTypeEntity.GUEST
    override val federated: UserTypeEntity
        get() = UserTypeEntity.FEDERATED
    override val external: UserTypeEntity
        get() = UserTypeEntity.EXTERNAL
    override val standard: UserTypeEntity
        get() = UserTypeEntity.STANDARD
    override val admin: UserTypeEntity
        get() = UserTypeEntity.ADMIN
    override val owner: UserTypeEntity
        get() = UserTypeEntity.OWNER
    override val service: UserTypeEntity
        get() = UserTypeEntity.SERVICE
    override val none: UserTypeEntity
        get() = UserTypeEntity.NONE

    override fun fromUserType(userType: UserType): UserTypeEntity = when (userType) {
        UserType.INTERNAL -> standard
        UserType.ADMIN -> admin
        UserType.OWNER -> owner
        UserType.EXTERNAL -> external
        UserType.FEDERATED -> federated
        UserType.GUEST -> guest
        UserType.SERVICE -> service
        UserType.NONE -> none
    }
}

class DomainUserTypeMapperImpl : DomainUserTypeMapper {

    override val guest: UserType
        get() = UserType.GUEST
    override val federated: UserType
        get() = UserType.FEDERATED
    override val external: UserType
        get() = UserType.EXTERNAL
    override val standard: UserType
        get() = UserType.INTERNAL
    override val admin: UserType
        get() = UserType.ADMIN
    override val owner: UserType
        get() = UserType.OWNER
    override val service: UserType
        get() = UserType.SERVICE
    override val none: UserType
        get() = UserType.NONE

    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserType {
        return when (userTypeEntity) {
            UserTypeEntity.STANDARD -> standard
            UserTypeEntity.EXTERNAL -> external
            UserTypeEntity.FEDERATED -> federated
            UserTypeEntity.GUEST -> guest
            UserTypeEntity.NONE -> none
            UserTypeEntity.OWNER -> owner
            UserTypeEntity.ADMIN -> admin
            UserTypeEntity.SERVICE -> service
            null -> none
        }
    }

}

interface UserEntityTypeMapper : UserTypeMapper<UserTypeEntity> {
    fun fromUserType(userType: UserType): UserTypeEntity
}

@Mockable
interface DomainUserTypeMapper : UserTypeMapper<UserType> {
    fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserType
}

interface UserTypeMapper<T> {

    val guest: T
    val federated: T
    val external: T
    val standard: T
    val admin: T
    val owner: T
    val service: T
    val none: T

    @Suppress("ReturnCount")
    fun fromTeamAndDomain(
        otherUserDomain: String,
        selfUserTeamId: String?,
        otherUserTeamId: String?,
        selfUserDomain: String,
        isService: Boolean,
    ): T = when {
        isService -> service
        isFromDifferentBackEnd(otherUserDomain, selfUserDomain) -> federated
        isFromTheSameTeam(otherUserTeamId, selfUserTeamId) -> standard
        selfUserIsTeamMember(selfUserTeamId) -> guest
        else -> none
    }

    private fun isFromDifferentBackEnd(otherUserDomain: String, selfDomain: String): Boolean =
        otherUserDomain.lowercase() != selfDomain.lowercase()

    private fun isFromTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        otherUserTeamId?.let { it == selfUserTeamId } ?: false

    private fun selfUserIsTeamMember(selfUserTeamId: String?) = selfUserTeamId != null

    fun teamRoleCodeToUserType(permissionCode: Int?, isService: Boolean = false): T =
        if (isService) service
        else when (permissionCode) {
            TeamRole.ExternalPartner.value -> external
            TeamRole.Member.value -> standard
            TeamRole.Admin.value -> admin
            TeamRole.Owner.value -> owner
            null -> standard
            else -> guest
        }
}
