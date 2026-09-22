/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserProfiles
import com.wire.kalium.logic.data.user.fetchProfileTeamMembers
import com.wire.kalium.logic.data.user.fetchUserProfiles
import com.wire.kalium.logic.data.user.isUserNotFoundFailure
import com.wire.kalium.logic.data.user.recoverMalformedQualifiedIdsIfNeeded
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.data.user.toIncompleteUserEntity
import com.wire.kalium.logic.data.user.toPartialUserEntity
import com.wire.kalium.logic.data.user.userProfiles
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.isTeamMember
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity

/** Required profile reads precede the atomic write; mapping and persistence are shared with the app. */
internal class MembershipUserLoader(
    private val users: UserDAO,
    private val api: UserDetailsApi?,
    private val teamsApi: TeamsApi?,
    private val selfUserId: UserId,
) {
    private val loaded = mutableMapOf<UserId, UserEntity>()
    private val removedIds = mutableSetOf<UserId>()

    suspend fun user(id: UserId): UserEntity? =
        if (id in removedIds) null else loaded[id] ?: users.getUserDetailsByQualifiedID(id.toDao())?.toSimpleEntity()

    suspend fun otherUserIds(): Set<UserId> =
        (users.allOtherUsersId().map { it.toModel() } + loaded.keys).filter { it != selfUserId && it !in removedIds }.toSet()

    suspend fun updateSelfProtocols(protocols: Set<SupportedProtocol>) {
        loaded[selfUserId] = checkNotNull(user(selfUserId)).copy(supportedProtocols = protocols.map { it.toDao() }.toSet())
    }

    /** Required after a self-protocol upload; preserve the app's complete profile-fetch policy. */
    suspend fun refreshAll(): UserProfiles {
        val ids = otherUserIds()
        if (ids.isEmpty()) return UserProfiles()
        val transport = checkNotNull(api) { "Protocol resolution requires authenticated profiles" }
        val removed = mutableSetOf<UserId>()
        val response = recoverMalformedQualifiedIdsIfNeeded(
            fetchResult = fetchUserProfiles(ids, selfUserId, transport),
            qualifiedUserIds = ids,
            deleteMalformedUser = { removed += it },
            retry = { fetchUserProfiles(it, selfUserId, transport) },
        ).orThrow()
        removedIds += removed
        removed.forEach { loaded.remove(it) }
        val incomplete = response.usersFailed.map { it.toIncompleteUserEntity() }
        incomplete.forEach { if (user(it.id.toModel()) == null) loaded[it.id.toModel()] = it }
        return map(response.usersFound).copy(incomplete = incomplete, removed = removed)
    }

    suspend fun markDeleted(id: UserId) {
        val known = user(id) ?: return
        loaded[id] = known.copy(
            deleted = true,
            userType = com.wire.kalium.persistence.dao.UserTypeEntity.NONE,
            previewAssetId = null,
            completeAssetId = null,
        )
    }

    suspend fun update(event: Event.User.Update) {
        val known = user(event.userId) ?: return
        val partial = event.toPartialUserEntity()
        loaded[event.userId] = known.copy(
            name = partial.name ?: known.name,
            handle = partial.handle ?: known.handle,
            email = partial.email ?: known.email,
            accentId = partial.accentId ?: known.accentId,
            previewAssetId = partial.previewAssetId ?: known.previewAssetId,
            completeAssetId = partial.completeAssetId ?: known.completeAssetId,
            supportedProtocols = partial.supportedProtocols ?: known.supportedProtocols,
        )
    }

    /** The app refreshes peer protocols for resolution and falls back on fetch failure. */
    suspend fun refresh(id: UserId): UserProfiles {
        val response = wrapApiRequest {
            checkNotNull(api) { "Profile refresh requires authenticated transport" }
                .getMultipleUsers(ListUserRequest.qualifiedIds(listOf(id.toApi())))
        }.getOrNull() ?: return UserProfiles()
        val profiles = response.usersFound.filter { it.id.toModel() == id }
        return map(profiles, allowFetchFailure = true)
    }

    /** Every connection status refreshes the peer; only the app's user-not-found case is tolerated. */
    suspend fun connectionPeer(id: UserId): UserProfiles = try {
        val profile = wrapApiRequest {
            checkNotNull(api) { "Connection profiles require authenticated transport" }.getUserInfo(id.toApi())
        }.orThrow()
        check(profile.id.toModel() == id) { "Required connection profile was not returned" }
        map(listOf(profile))
    } catch (failure: KaliumSyncException) {
        if (failure.coreFailureCause.isUserNotFoundFailure()) UserProfiles() else throw failure
    }

    suspend fun load(ids: Set<UserId>): UserProfiles {
        val known = users.getUsersDetailsByQualifiedIDList(ids.map { it.toDao() })
        val missing = ids.filterNot { id -> id in loaded || known.any { it.id == id.toDao() && !it.name.isNullOrBlank() } }
        if (missing.isEmpty()) return UserProfiles()
        val profiles = missing.chunked(USER_BATCH_SIZE).flatMap { chunk ->
            val response = wrapApiRequest {
                checkNotNull(api) { "Membership profiles require authenticated transport" }
                    .getMultipleUsers(ListUserRequest.qualifiedIds(chunk.map { it.toApi() }))
            }.orThrow()
            check(response.usersFailed.isEmpty() && response.usersFound.map { it.id.toModel() }.toSet().containsAll(chunk)) {
                "Required membership profiles were not returned"
            }
            response.usersFound
        }
        return map(profiles)
    }

    private suspend fun map(
        profiles: List<com.wire.kalium.network.api.model.UserProfileDTO>,
        allowFetchFailure: Boolean = false,
    ): UserProfiles {
        val selfTeam = users.getUserDetailsByQualifiedID(selfUserId.toDao())?.team
        val teamMembers = if (profiles.any { it.isTeamMember(selfTeam, selfUserId.domain) }) {
            val result = fetchProfileTeamMembers(
                profiles,
                selfUserId,
                selfTeam,
                checkNotNull(teamsApi) { "Team profiles require authenticated transport" }
            )
            if (allowFetchFailure && result is com.wire.kalium.common.functional.Either.Left) return UserProfiles()
            result.orThrow()
        } else emptyList()
        return userProfiles(profiles, teamMembers, selfUserId, selfTeam).also { mapped ->
            mapped.users.forEach { profile ->
                val id = profile.id.toModel()
                // Profile upsert does not replace the locally selected conversation in SQL.
                loaded[id] = profile.copy(activeOneOnOneConversationId = user(id)?.activeOneOnOneConversationId)
            }
        }
    }

    private companion object {
        const val USER_BATCH_SIZE = 100
    }
}
