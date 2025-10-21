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
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.UserTypeInfoEntity
import io.mockative.Mockable

class UserEntityTypeMapperImpl : UserEntityTypeMapper {

    val guest: UserTypeEntity
        get() = UserTypeEntity.GUEST
    val federated: UserTypeEntity
        get() = UserTypeEntity.FEDERATED
    val external: UserTypeEntity
        get() = UserTypeEntity.EXTERNAL
    val standard: UserTypeEntity
        get() = UserTypeEntity.STANDARD
    val admin: UserTypeEntity
        get() = UserTypeEntity.ADMIN
    val owner: UserTypeEntity
        get() = UserTypeEntity.OWNER
    val service: UserTypeEntity
        get() = UserTypeEntity.SERVICE
    val none: UserTypeEntity
        get() = UserTypeEntity.NONE
    val app: UserTypeEntity
        get() = UserTypeEntity.APP

    /**
     * Maps API user type combined with inference logic to determine the specific user type.
     *
     * The API provides a high-level type hint (REGULAR, APP, BOT), and we combine it with
     * team and domain information to determine the specific domain user type.
     *
     * @param apiUserTypeDTO The user type from the API (nullable for backward compatibility)
     * @param otherUserDomain The domain of the other user
     * @param selfUserTeamId The team ID of the self user
     * @param otherUserTeamId The team ID of the other user
     * @param selfUserDomain The domain of the self user
     * @return The specific domain user type
     */
    @Suppress("ReturnCount", "LongParameterList")
    override fun fromApiTypeAndTeamAndDomain(
        apiUserTypeDTO: UserTypeDTO?,
        otherUserDomain: String,
        selfUserTeamId: String?,
        otherUserTeamId: String?,
        selfUserDomain: String,
        isLegacyBot: Boolean
    ): UserTypeEntity =
        when (apiUserTypeDTO) {
            UserTypeDTO.APP -> app
            UserTypeDTO.BOT -> service
            UserTypeDTO.REGULAR, null -> {
                // For REGULAR users or when type is not provided (backward compatibility),
                // use the existing inference logic
                fromTeamAndDomain(
                    otherUserDomain = otherUserDomain,
                    selfUserTeamId = selfUserTeamId,
                    otherUserTeamId = otherUserTeamId,
                    selfUserDomain = selfUserDomain,
                    isService = isLegacyBot
                )
            }
        }

    override fun teamRoleCodeToUserType(permissionCode: Int?, isService: Boolean): UserTypeEntity =
        if (isService) service
        else when (permissionCode) {
            TeamRole.ExternalPartner.value -> external
            TeamRole.Member.value -> standard
            TeamRole.Admin.value -> admin
            TeamRole.Owner.value -> owner
            null -> standard
            else -> guest
        }

    private fun fromTeamAndDomain(
        otherUserDomain: String,
        selfUserTeamId: String?,
        otherUserTeamId: String?,
        selfUserDomain: String,
        isService: Boolean,
    ): UserTypeEntity = when {
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

    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserTypeInfoEntity = when (userTypeEntity) {
        UserTypeEntity.STANDARD -> UserTypeInfoEntity.Regular(standard)
        UserTypeEntity.EXTERNAL -> UserTypeInfoEntity.Regular(external)
        UserTypeEntity.FEDERATED -> UserTypeInfoEntity.Regular(federated)
        UserTypeEntity.GUEST -> UserTypeInfoEntity.Regular(guest)
        UserTypeEntity.NONE -> UserTypeInfoEntity.Regular(none)
        UserTypeEntity.OWNER -> UserTypeInfoEntity.Regular(owner)
        UserTypeEntity.ADMIN -> UserTypeInfoEntity.Regular(admin)
        UserTypeEntity.SERVICE -> UserTypeInfoEntity.Bot
        UserTypeEntity.APP -> UserTypeInfoEntity.App
    }

    override fun fromUserTypeInfo(userTypeInfo: UserTypeInfo): UserTypeInfoEntity = when (userTypeInfo) {
        is UserTypeInfo.Regular -> {
            when (userTypeInfo.type) {
                UserType.INTERNAL -> UserTypeInfoEntity.Regular(standard)
                UserType.ADMIN -> UserTypeInfoEntity.Regular(admin)
                UserType.OWNER -> UserTypeInfoEntity.Regular(owner)
                UserType.EXTERNAL -> UserTypeInfoEntity.Regular(external)
                UserType.FEDERATED -> UserTypeInfoEntity.Regular(federated)
                UserType.GUEST -> UserTypeInfoEntity.Regular(guest)
                UserType.NONE -> UserTypeInfoEntity.Regular(none)
            }
        }

        is UserTypeInfo.Bot -> UserTypeInfoEntity.Bot
        is UserTypeInfo.App -> UserTypeInfoEntity.App
    }

}

class DomainUserTypeMapperImpl : DomainUserTypeMapper {

    val guest: UserType
        get() = UserType.GUEST
    val federated: UserType
        get() = UserType.FEDERATED
    val external: UserType
        get() = UserType.EXTERNAL
    val standard: UserType
        get() = UserType.INTERNAL
    val admin: UserType
        get() = UserType.ADMIN
    val owner: UserType
        get() = UserType.OWNER
    val none: UserType
        get() = UserType.NONE

    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserTypeInfo {
        return when (userTypeEntity) {
            UserTypeEntity.STANDARD -> UserTypeInfo.Regular(standard)
            UserTypeEntity.EXTERNAL -> UserTypeInfo.Regular(external)
            UserTypeEntity.FEDERATED -> UserTypeInfo.Regular(federated)
            UserTypeEntity.GUEST -> UserTypeInfo.Regular(guest)
            UserTypeEntity.NONE -> UserTypeInfo.Regular(none)
            UserTypeEntity.OWNER -> UserTypeInfo.Regular(owner)
            UserTypeEntity.ADMIN -> UserTypeInfo.Regular(admin)
            UserTypeEntity.SERVICE -> UserTypeInfo.Bot
            UserTypeEntity.APP -> UserTypeInfo.App
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
    }

    override fun fromUserTypeInfoEntity(userTypeInfoEntity: UserTypeInfoEntity) = fromUserTypeEntity(userTypeInfoEntity.type)
}

interface UserEntityTypeMapper : UserTypeMapper<UserTypeEntity> {
    fun fromUserTypeEntity(userTypeEntity: UserTypeEntity): UserTypeInfoEntity
    fun fromUserTypeInfo(userType: UserTypeInfo): UserTypeInfoEntity
    fun teamRoleCodeToUserType(permissionCode: Int?, isService: Boolean = false): UserTypeEntity

    @Suppress("LongParameterList")
    fun fromApiTypeAndTeamAndDomain(
        apiUserTypeDTO: UserTypeDTO?,
        otherUserDomain: String,
        selfUserTeamId: String?,
        otherUserTeamId: String?,
        selfUserDomain: String,
        isLegacyBot: Boolean = false
    ): UserTypeEntity
}

@Mockable
interface DomainUserTypeMapper : UserTypeMapper<UserType> {
    fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserTypeInfo
    fun fromUserType(userType: UserType): UserTypeInfo
    fun fromUserTypeInfoEntity(userTypeInfoEntity: UserTypeInfoEntity): UserTypeInfo
}

interface UserTypeMapper<T> {

    private fun isFromDifferentBackEnd(otherUserDomain: String, selfDomain: String): Boolean =
        otherUserDomain.lowercase() != selfDomain.lowercase()

    private fun isFromTheSameTeam(otherUserTeamId: String?, selfUserTeamId: String?): Boolean =
        otherUserTeamId?.let { it == selfUserTeamId } ?: false

    private fun selfUserIsTeamMember(selfUserTeamId: String?) = selfUserTeamId != null
}
