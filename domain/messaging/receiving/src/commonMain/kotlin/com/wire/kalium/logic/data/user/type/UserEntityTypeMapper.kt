/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.user.type

import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.persistence.dao.UserTypeEntity

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

    override fun fromUserTypeInfo(userTypeInfo: UserTypeInfo): UserTypeEntity = when (userTypeInfo) {
        is UserTypeInfo.Regular -> {
            when (userTypeInfo.type) {
                UserType.INTERNAL -> standard
                UserType.ADMIN -> admin
                UserType.OWNER -> owner
                UserType.EXTERNAL -> external
                UserType.FEDERATED -> federated
                UserType.GUEST -> guest
                UserType.NONE -> none
            }
        }

        is UserTypeInfo.Bot -> service
        is UserTypeInfo.App -> app
    }

}

interface UserEntityTypeMapper {
    fun fromUserTypeInfo(userTypeInfo: UserTypeInfo): UserTypeEntity
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
