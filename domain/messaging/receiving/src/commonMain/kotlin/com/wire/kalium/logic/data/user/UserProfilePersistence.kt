/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.user

import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapperImpl
import com.wire.kalium.network.api.authenticated.teams.TeamMemberDTO
import com.wire.kalium.network.api.authenticated.teams.TeamMemberIdList
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.model.isLegacyBot
import com.wire.kalium.network.api.model.isTeamMember
import com.wire.kalium.persistence.dao.AppDAO
import com.wire.kalium.persistence.dao.AppEntity
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlin.time.Instant

fun UserProfileDTO.toUserEntity(connectionState: ConnectionEntity.State, userTypeEntity: UserTypeEntity): UserEntity {
    val userProfile = this
    return UserEntity(
        id = userProfile.id.toDao(),
        name = userProfile.name,
        handle = userProfile.handle,
        email = userProfile.email,
        phone = null,
        accentId = userProfile.accentId,
        team = userProfile.teamId,
        previewAssetId = userProfile.assets.getPreviewAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) },
        completeAssetId = userProfile.assets.getCompleteAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) },
        connectionStatus = connectionState,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = userTypeEntity,
        botService = userProfile.service?.let { BotIdEntity(it.id, it.provider) },
        deleted = userProfile.deleted ?: false,
        expiresAt = userProfile.expiresAt?.let { Instant.parse(it) },
        defederated = false,
        supportedProtocols = userProfile.supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
        activeOneOnOneConversationId = null
    )
}

/** Canonical profile effects; the app invokes its legal-hold hooks before persisting these. */
data class UserProfiles(
    val teamMembers: List<UserEntity> = emptyList(),
    val others: List<UserEntity> = emptyList(),
    val apps: List<AppEntity> = emptyList(),
    val incomplete: List<UserEntity> = emptyList(),
    val removed: Set<UserId> = emptySet(),
) {
    val users: List<UserEntity> get() = teamMembers + others

    suspend fun persist(users: UserDAO, apps: AppDAO) {
        removed.forEach { users.deleteUserByQualifiedID(it.toDao()) }
        if (incomplete.isNotEmpty()) users.insertOrIgnoreUsers(incomplete)
        if (teamMembers.isNotEmpty()) {
            users.upsertUsers(teamMembers)
            users.upsertConnectionStatuses(teamMembers.associate { it.id to it.connectionStatus })
        }
        if (others.isNotEmpty()) users.upsertUsers(others)
        if (this.apps.isNotEmpty()) apps.upsertApps(this.apps)
    }
}

fun userProfiles(
    profiles: List<UserProfileDTO>,
    teamMembers: List<TeamMemberDTO>,
    selfUserId: UserId,
    selfTeamId: String?,
    types: UserEntityTypeMapper = UserEntityTypeMapperImpl(),
): UserProfiles {
    val byId = teamMembers.associateBy { it.nonQualifiedUserId }
    val (teamProfiles, others) = profiles.partition { byId.containsKey(it.id.value) }
    return UserProfiles(
        teamMembers = teamProfiles.map { profile ->
            val type = when {
                profile.isLegacyBot() || profile.type == UserTypeDTO.BOT -> UserTypeEntity.SERVICE
                profile.type == UserTypeDTO.APP -> UserTypeEntity.APP
                else -> types.teamRoleCodeToUserType(byId[profile.id.value]?.permissions?.own)
            }
            profile.toUserEntity(ConnectionEntity.State.ACCEPTED, type)
        },
        others = others.map { profile ->
            profile.toUserEntity(
                ConnectionEntity.State.NOT_CONNECTED,
                types.fromApiTypeAndTeamAndDomain(
                    profile.type,
                    profile.id.domain,
                    selfTeamId,
                    profile.teamId,
                    selfUserId.domain,
                    profile.isLegacyBot()
                )
            )
        },
        apps = profiles.filter { it.type == UserTypeDTO.APP }.map { it.toAppEntity() },
    )
}

suspend fun fetchProfileTeamMembers(
    profiles: List<UserProfileDTO>,
    selfUserId: UserId,
    selfTeamId: String?,
    api: TeamsApi,
) = if (selfTeamId == null) Either.Right(emptyList<TeamMemberDTO>()) else {
    profiles.filter { it.isTeamMember(selfTeamId, selfUserId.domain) }.map { it.id.value }
        .chunked(TEAM_MEMBER_BATCH_SIZE)
        .foldToEitherWhileRight(emptyList<TeamMemberDTO>()) { chunk, acc ->
            wrapApiRequest { api.getTeamMembersByIds(selfTeamId, TeamMemberIdList(chunk)) }.map { (acc + it.members).distinct() }
        }
}

fun UserProfileDTO.toAppEntity() = AppEntity(
    id.toDao(),
    name,
    app?.description.orEmpty(),
    app?.category,
    teamId,
    assets.getPreviewAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
    assets.getCompleteAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
)

private const val TEAM_MEMBER_BATCH_SIZE = 500

fun NetworkQualifiedId.toIncompleteUserEntity(): UserEntity {
    return UserEntity(
        id = toDao(),
        name = null,
        handle = null,
        email = null,
        phone = null,
        accentId = 1,
        team = null,
        connectionStatus = ConnectionEntity.State.ACCEPTED,
        previewAssetId = null,
        completeAssetId = null,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = UserTypeEntity.STANDARD,
        botService = null,
        deleted = false,
        hasIncompleteMetadata = true,
        expiresAt = null,
        defederated = false,
        supportedProtocols = null,
        activeOneOnOneConversationId = null
    )
}
