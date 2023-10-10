/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.cache.Cache
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import com.wire.kalium.persistence.User as SQLDelightUser

class UserMapper {
    fun toModel(user: SQLDelightUser): UserEntity {
        return UserEntity(
            id = user.qualified_id,
            name = user.name,
            handle = user.handle,
            email = user.email,
            phone = user.phone,
            accentId = user.accent_id,
            team = user.team,
            connectionStatus = user.connection_status,
            previewAssetId = user.preview_asset_id,
            completeAssetId = user.complete_asset_id,
            availabilityStatus = user.user_availability_status,
            userType = user.user_type,
            botService = user.bot_service,
            deleted = user.deleted,
            hasIncompleteMetadata = user.incomplete_metadata,
            expiresAt = user.expires_at,
            defederated = user.defederated,
            supportedProtocols = user.supported_protocols,
            activeOneOnOneConversationId = user.active_one_on_one_conversation_id
        )
    }

    @Suppress("LongParameterList")
    fun toUserAndTeamPairModel(
        qualifiedId: QualifiedIDEntity,
        name: String?,
        handle: String?,
        email: String?,
        phone: String?,
        accentId: Int,
        team: String?,
        connectionStatus: ConnectionEntity.State,
        previewAssetId: QualifiedIDEntity?,
        completeAssetId: QualifiedIDEntity?,
        userAvailabilityStatus: UserAvailabilityStatusEntity,
        userType: UserTypeEntity,
        botService: BotIdEntity?,
        deleted: Boolean,
        hasIncompleteMetadata: Boolean,
        expiresAt: Instant?,
        defederated: Boolean,
        supportedProtocols: Set<SupportedProtocolEntity>?,
        oneOnOneConversationId: QualifiedIDEntity?,
        id: String?,
        teamName: String?,
        teamIcon: String?,
    ): Pair<UserEntity, TeamEntity?> {
        val userEntity = UserEntity(
            id = qualifiedId,
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = team,
            connectionStatus = connectionStatus,
            previewAssetId = previewAssetId,
            completeAssetId = completeAssetId,
            availabilityStatus = userAvailabilityStatus,
            userType = userType,
            botService = botService,
            deleted = deleted,
            hasIncompleteMetadata = hasIncompleteMetadata,
            expiresAt = expiresAt,
            defederated = defederated,
            supportedProtocols = supportedProtocols,
            activeOneOnOneConversationId = oneOnOneConversationId
        )

        val teamEntity = if (team != null && teamName != null && teamIcon != null) {
            TeamEntity(team, teamName, teamIcon)
        } else null

        return userEntity to teamEntity
    }

    fun toModelMinimized(
        userId: QualifiedIDEntity,
        name: String?,
        assetId: QualifiedIDEntity?,
        userTypeEntity: UserTypeEntity
    ) = UserEntityMinimized(
        userId,
        name,
        assetId,
        userTypeEntity
    )
}

@Suppress("TooManyFunctions")
class UserDAOImpl internal constructor(
    private val userQueries: UsersQueries,
    private val userCache: Cache<UserIDEntity, Flow<UserEntity?>>,
    private val databaseScope: CoroutineScope,
    private val queriesContext: CoroutineContext
) : UserDAO {

    val mapper = UserMapper()
    override suspend fun upsertUser(user: UserEntity) {
        upsertUsers(listOf(user))
    }

    override suspend fun insertOrIgnoreUsers(users: List<UserEntity>) = withContext(queriesContext) {
        userQueries.transaction {
            for (user: UserEntity in users) {
                userQueries.insertOrIgnoreUser(
                    qualified_id = user.id,
                    name = user.name,
                    handle = user.handle,
                    email = user.email,
                    phone = user.phone,
                    accent_id = user.accentId,
                    team = user.team,
                    preview_asset_id = user.previewAssetId,
                    complete_asset_id = user.completeAssetId,
                    user_type = user.userType,
                    bot_service = user.botService,
                    incomplete_metadata = false,
                    expires_at = user.expiresAt,
                    connection_status = user.connectionStatus,
                    deleted = user.deleted,
                    supported_protocols = user.supportedProtocols
                )
            }
        }
    }

    override suspend fun updateUser(id: UserIDEntity, update: PartialUserEntity) = withContext(queriesContext) {
        userQueries.updateUser(
            name = update.name,
            handle = update.handle,
            email = update.email,
            accent_id = update.accentId,
            preview_asset_id = update.previewAssetId,
            complete_asset_id = update.completeAssetId,
            supported_protocols = update.supportedProtocols,
            id
        ).executeAsOne() > 0
    }

    override suspend fun upsertUsers(users: List<UserEntity>) = withContext(queriesContext) {
        userQueries.transaction {
            for (user: UserEntity in users) {
                userQueries.insertUser(
                    qualified_id = user.id,
                    name = user.name,
                    handle = user.handle,
                    email = user.email,
                    phone = user.phone,
                    accent_id = user.accentId,
                    team = user.team,
                    preview_asset_id = user.previewAssetId,
                    complete_asset_id = user.completeAssetId,
                    user_type = user.userType,
                    bot_service = user.botService,
                    incomplete_metadata = user.hasIncompleteMetadata,
                    expires_at = user.expiresAt,
                    connection_status = user.connectionStatus,
                    deleted = user.deleted,
                    supported_protocols = user.supportedProtocols,
                    active_one_on_one_conversation_id = user.activeOneOnOneConversationId
                )
            }
        }
    }

    override suspend fun upsertTeamMemberUserTypes(users: Map<QualifiedIDEntity, UserTypeEntity>) {
        userQueries.transaction {
            for (user: Map.Entry<QualifiedIDEntity, UserTypeEntity> in users) {
                userQueries.upsertTeamMemberUserType(user.key, ConnectionEntity.State.ACCEPTED, user.value)
            }
        }
    }

    override suspend fun getAllUsers(): Flow<List<UserEntity>> = userQueries.selectAllUsers()
        .asFlow()
        .flowOn(queriesContext)
        .mapToList()
        .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?> = userCache.get(qualifiedID) {
        userQueries.selectByQualifiedId(listOf(qualifiedID))
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { mapper.toModel(it) } }
            .shareIn(databaseScope, Lazily, 1)
    }

    override suspend fun getUserWithTeamByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<Pair<UserEntity, TeamEntity?>?> =
        userQueries.selectWithTeamByQualifiedId(listOf(qualifiedID), mapper::toUserAndTeamPairModel)
            .asFlow()
            .mapToOneOrNull()

    override suspend fun getUserMinimizedByQualifiedID(qualifiedID: QualifiedIDEntity): UserEntityMinimized? =
        withContext(queriesContext) {
            userQueries.selectMinimizedByQualifiedId(listOf(qualifiedID)) { qualifiedId, name, completeAssetId, userType ->
                mapper.toModelMinimized(qualifiedId, name, completeAssetId, userType)
            }.executeAsOneOrNull()
        }

    override suspend fun getUsersByQualifiedIDList(qualifiedIDList: List<QualifiedIDEntity>): List<UserEntity> =
        withContext(queriesContext) {
            userQueries.selectByQualifiedId(qualifiedIDList)
                .executeAsList()
                .map { mapper.toModel(it) }
        }

    override suspend fun getUserByNameOrHandleOrEmailAndConnectionStates(
        searchQuery: String,
        connectionStates: List<ConnectionEntity.State>
    ): Flow<List<UserEntity>> = userQueries.selectByNameOrHandleOrEmailAndConnectionState(searchQuery, connectionStates)
        .asFlow()
        .flowOn(queriesContext)
        .mapToList()
        .map { it.map(mapper::toModel) }

    override suspend fun getUserByHandleAndConnectionStates(
        handle: String,
        connectionStates: List<ConnectionEntity.State>
    ) = userQueries.selectByHandleAndConnectionState(handle, connectionStates)
        .asFlow()
        .flowOn(queriesContext)
        .mapToList()
        .map { it.map(mapper::toModel) }

    override suspend fun getUsersWithOneOnOneConversation(): List<UserEntity> = withContext(queriesContext) {
        userQueries.selectUsersWithOneOnOne().executeAsList().map(mapper::toModel)
    }

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity) = withContext(queriesContext) {
        userQueries.deleteUser(qualifiedID)
    }

    override suspend fun markUserAsDeleted(qualifiedID: QualifiedIDEntity) = withContext(queriesContext) {
        userQueries.markUserAsDeleted(user_type = UserTypeEntity.NONE, qualified_id = qualifiedID)
    }

    override suspend fun markUserAsDefederated(qualifiedID: QualifiedIDEntity) {
        userQueries.markUserAsDefederated(qualifiedID)
    }

    override suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String) = withContext(queriesContext) {
        userQueries.updateUserhandle(handle, qualifiedID)
    }

    override suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity) =
        withContext(queriesContext) {
            userQueries.updateUserAvailabilityStatus(status, qualifiedID)
        }

    override fun observeUsersNotInConversation(conversationId: QualifiedIDEntity): Flow<List<UserEntity>> =
        userQueries.getUsersNotPartOfTheConversation(conversationId)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
            .map { it.map(mapper::toModel) }

    override suspend fun getUsersNotInConversationByNameOrHandleOrEmail(
        conversationId: QualifiedIDEntity,
        searchQuery: String
    ): Flow<List<UserEntity>> =
        userQueries.getUsersNotInConversationByNameOrHandleOrEmail(conversationId, searchQuery)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
            .map { it.map(mapper::toModel) }

    override suspend fun getUsersNotInConversationByHandle(conversationId: QualifiedIDEntity, handle: String): Flow<List<UserEntity>> =
        userQueries.getUsersNotInConversationByHandle(conversationId, handle)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
            .map { it.map(mapper::toModel) }

    override suspend fun insertOrIgnoreUserWithConnectionStatus(qualifiedID: QualifiedIDEntity, connectionStatus: ConnectionEntity.State) =
        withContext(queriesContext) {
            userQueries.insertOrIgnoreUserIdWithConnectionStatus(qualifiedID, connectionStatus)
        }

    override suspend fun observeAllUsersByConnectionStatus(connectionState: ConnectionEntity.State): Flow<List<UserEntity>> =
        withContext(queriesContext) {
            userQueries.selectAllUsersWithConnectionStatus(connectionState)
                .asFlow()
                .flowOn(queriesContext)
                .mapToList()
                .map { it.map(mapper::toModel) }
        }

    override suspend fun getAllUsersByTeam(teamId: String): List<UserEntity> = withContext(queriesContext) {
        userQueries.selectUsersByTeam(teamId)
            .executeAsList()
            .map(mapper::toModel)
    }

    override suspend fun updateUserDisplayName(selfUserId: QualifiedIDEntity, displayName: String) = withContext(queriesContext) {
        userQueries.updateUserDisplayName(displayName, selfUserId)
    }

    override suspend fun removeUserAsset(assetId: QualifiedIDEntity) {
        userQueries.updateUserAsset(null, null, assetId)
    }

    override suspend fun getUsersWithoutMetadata() = withContext(queriesContext) {
        userQueries.selectUsersWithoutMetadata()
            .executeAsList()
            .map(mapper::toModel)
    }

    override suspend fun allOtherUsersId(): List<UserIDEntity> = withContext(queriesContext) {
        userQueries.userIdsWithoutSelf().executeAsList()
    }

    override suspend fun updateUserSupportedProtocols(selfUserId: QualifiedIDEntity, supportedProtocols: Set<SupportedProtocolEntity>) =
        withContext(queriesContext) {
            userQueries.updateUserSupportedProtocols(supportedProtocols, selfUserId)
        }

    override suspend fun updateActiveOneOnOneConversation(userId: QualifiedIDEntity, conversationId: QualifiedIDEntity) =
        withContext(queriesContext) {
            userQueries.updateOneOnOnConversationId(conversationId, userId)
        }
}
