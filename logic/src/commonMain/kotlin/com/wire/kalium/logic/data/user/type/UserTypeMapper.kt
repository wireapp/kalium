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
import com.wire.kalium.persistence.dao.UserTypeInfoEntity
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
    override val app: UserTypeEntity
        get() = UserTypeEntity.APP

    override fun fromUserType(userType: UserType): UserTypeInfoEntity = when (userType) {
        UserType.INTERNAL -> UserTypeInfoEntity.Regular(standard)
        UserType.ADMIN -> UserTypeInfoEntity.Regular(admin)
        UserType.OWNER -> UserTypeInfoEntity.Regular(owner)
        UserType.EXTERNAL -> UserTypeInfoEntity.Regular(external)
        UserType.FEDERATED -> UserTypeInfoEntity.Regular(federated)
        UserType.GUEST -> UserTypeInfoEntity.Regular(guest)
        UserType.NONE -> UserTypeInfoEntity.Regular(none)
        UserType.SERVICE -> UserTypeInfoEntity.Bot(service)
        UserType.APP -> UserTypeInfoEntity.App(app)
    }

    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserTypeInfoEntity = when (userTypeEntity) {
        UserTypeEntity.STANDARD -> UserTypeInfoEntity.Regular(standard)
        UserTypeEntity.EXTERNAL -> UserTypeInfoEntity.Regular(external)
        UserTypeEntity.FEDERATED -> UserTypeInfoEntity.Regular(federated)
        UserTypeEntity.GUEST -> UserTypeInfoEntity.Regular(guest)
        UserTypeEntity.NONE -> UserTypeInfoEntity.Regular(none)
        UserTypeEntity.OWNER -> UserTypeInfoEntity.Regular(owner)
        UserTypeEntity.ADMIN -> UserTypeInfoEntity.Regular(admin)
        UserTypeEntity.SERVICE -> UserTypeInfoEntity.Bot(service)
        UserTypeEntity.APP -> UserTypeInfoEntity.App(app)
    }

    override fun fromUserTypeInfo(userType: UserTypeInfo): UserTypeInfoEntity = fromUserType(userType.type)

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
    override val app: UserType
        get() = UserType.APP


    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserTypeInfo {
        return when (userTypeEntity) {
            UserTypeEntity.STANDARD -> UserTypeInfo.Regular(standard)
            UserTypeEntity.EXTERNAL -> UserTypeInfo.Regular(external)
            UserTypeEntity.FEDERATED -> UserTypeInfo.Regular(federated)
            UserTypeEntity.GUEST -> UserTypeInfo.Regular(guest)
            UserTypeEntity.NONE -> UserTypeInfo.Regular(none)
            UserTypeEntity.OWNER -> UserTypeInfo.Regular(owner)
            UserTypeEntity.ADMIN -> UserTypeInfo.Regular(admin)
            UserTypeEntity.SERVICE -> UserTypeInfo.Bot(service)
            UserTypeEntity.APP -> UserTypeInfo.App(app)
            null -> UserTypeInfo.Regular(none)
        }
    }

    override fun fromUserType(userType: UserType): UserTypeInfo = when (userType) {
        UserType.INTERNAL -> UserTypeInfo.Regular(standard)
        UserType.ADMIN -> UserTypeInfo.Regular(admin)
        UserType.OWNER -> UserTypeInfo.Regular(owner)
        UserType.EXTERNAL -> UserTypeInfo.Regular(external)
        UserType.FEDERATED -> UserTypeInfo.Regular(federated)
        UserType.GUEST -> UserTypeInfo.Regular(guest)
        UserType.NONE -> UserTypeInfo.Regular(none)
        UserType.SERVICE -> UserTypeInfo.Bot(service)
        UserType.APP -> UserTypeInfo.App(app)
    }

    override fun fromUserTypeInfoEntity(userTypeInfoEntity: UserTypeInfoEntity) = fromUserTypeEntity(userTypeInfoEntity.type)
}

interface UserEntityTypeMapper : UserTypeMapper<UserTypeEntity> {
    fun fromUserType(userType: UserType): UserTypeInfoEntity
    fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserTypeInfoEntity
    fun fromUserTypeInfo(userType: UserTypeInfo): UserTypeInfoEntity
}

@Mockable
interface DomainUserTypeMapper : UserTypeMapper<UserType> {
    fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserTypeInfo
    fun fromUserType(userType: UserType): UserTypeInfo
    fun fromUserTypeInfoEntity(userTypeInfoEntity: UserTypeInfoEntity): UserTypeInfo
}

interface UserTypeMapper<T> {

    val guest: T
    val federated: T
    val external: T
    val standard: T
    val admin: T
    val owner: T
    val service: T
    val app: T
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

    /**
     * Maps API user type combined with inference logic to determine the specific user type.
     *
     * The API provides a high-level type hint (REGULAR, APP, BOT), and we combine it with
     * team and domain information to determine the specific domain user type.
     *
     * @param apiUserType The user type from the API (nullable for backward compatibility)
     * @param otherUserDomain The domain of the other user
     * @param selfUserTeamId The team ID of the self user
     * @param otherUserTeamId The team ID of the other user
     * @param selfUserDomain The domain of the self user
     * @return The specific domain user type
     */
    @Suppress("ReturnCount")
    fun fromApiTypeAndTeamAndDomain(
        apiUserType: com.wire.kalium.network.api.model.UserType?,
        otherUserDomain: String,
        selfUserTeamId: String?,
        otherUserTeamId: String?,
        selfUserDomain: String,
    ): T = when (apiUserType) {
        com.wire.kalium.network.api.model.UserType.APP -> app
        com.wire.kalium.network.api.model.UserType.BOT -> service
        com.wire.kalium.network.api.model.UserType.REGULAR, null -> {
            // For REGULAR users or when type is not provided (backward compatibility),
            // use the existing inference logic
            fromTeamAndDomain(
                otherUserDomain = otherUserDomain,
                selfUserTeamId = selfUserTeamId,
                otherUserTeamId = otherUserTeamId,
                selfUserDomain = selfUserDomain,
                isService = false
            )
        }
    }

    private fun isFromDifferentBackEnd(otherUserDomain: String, selfDomain: String): Boolean =
        otherUserDomain.lowercase() != selfDomain.lowercase()

    private fun isFromTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        otherUserTeamId?.let { it == selfUserTeamId } ?: false

    private fun selfUserIsTeamMember(selfUserTeamId: String?) = selfUserTeamId != null

    // todo ym. verify how this fits with new user types
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
