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

package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.cache.FlowCache
import com.wire.kalium.persistence.dao.conversation.NameAndHandleEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import com.wire.kalium.persistence.User as SQLDelightUser
import com.wire.kalium.persistence.UserDetails as SQLDelightUserDetails

class UserMapper {
    fun toDetailsModel(user: SQLDelightUserDetails): UserDetailsEntity {
        return UserDetailsEntity(
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
            isProteusVerified = user.is_proteus_verified == 1L,
            activeOneOnOneConversationId = user.active_one_on_one_conversation_id,
            isUnderLegalHold = user.is_under_legal_hold == 1L,
        )
    }

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
        isVerifiedProteus: Long,
        isUnderLegalHold: Long,
        id: String?,
        teamName: String?,
        teamIcon: String?,
    ): Pair<UserDetailsEntity, TeamEntity?> {
        val userEntity = UserDetailsEntity(
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
            isProteusVerified = isVerifiedProteus == 1L,
            supportedProtocols = supportedProtocols,
            activeOneOnOneConversationId = oneOnOneConversationId,
            isUnderLegalHold = isUnderLegalHold == 1L,
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
        userTypeEntity: UserTypeEntity,
        accentId: Int
    ) = UserEntityMinimized(
        userId,
        name,
        assetId,
        userTypeEntity,
        accentId
    )
}

@Suppress("TooManyFunctions")
class UserDAOImpl internal constructor(
    private val userQueries: UsersQueries,
    private val userCache: FlowCache<UserIDEntity, UserDetailsEntity?>,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : UserDAO {

    val mapper = UserMapper()
    override suspend fun upsertUser(user: UserEntity) {
        upsertUsers(listOf(user))
    }

    override suspend fun insertOrIgnoreUsers(users: List<UserEntity>) = withContext(writeDispatcher.value) {
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

    override suspend fun updateUser(update: PartialUserEntity) {
        withContext(writeDispatcher.value) {
            userQueries.updateUser(
                name = update.name,
                handle = update.handle,
                email = update.email,
                accent_id = update.accentId?.toLong(),
                preview_asset_id = update.previewAssetId,
                complete_asset_id = update.completeAssetId,
                supported_protocols = update.supportedProtocols,
                update.id
            )
        }
    }

    override suspend fun updateUser(users: List<PartialUserEntity>) = withContext(writeDispatcher.value) {
        userQueries.transaction {
            for (user: PartialUserEntity in users) {
                userQueries.updatePartialUserInformation(
                    name = user.name,
                    handle = user.handle,
                    email = user.email,
                    accent_id = user.accentId?.toLong(),
                    preview_asset_id = user.previewAssetId,
                    complete_asset_id = user.completeAssetId,
                    supported_protocols = user.supportedProtocols,
                    user.id
                )
            }
        }
    }

    private fun insertUser(user: UserEntity): Boolean {
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
        return userQueries.selectChanges().executeAsOne() > 0
    }

    override suspend fun upsertUsers(users: List<UserEntity>) = withContext(writeDispatcher.value) {
        userQueries.transaction {
            val anyInsertedOrModified = users.map { user ->
                if (user.deleted) {
                    // mark as deleted and remove from groups
                    safeMarkAsDeletedAndRemoveFromGroupConversation(user.id)
                } else {
                    insertUser(user)
                }
            }.any { it }
            if (!anyInsertedOrModified) {
                // rollback the transaction if no changes were made so that it doesn't notify other queries if not needed
                this.rollback()
            }
        }
    }

    override suspend fun upsertTeamMemberUserTypes(users: Map<QualifiedIDEntity, UserTypeEntity>) = withContext(writeDispatcher.value) {
        userQueries.transaction {
            for (user: Map.Entry<QualifiedIDEntity, UserTypeEntity> in users) {
                userQueries.upsertTeamMemberUserType(user.key, ConnectionEntity.State.ACCEPTED, user.value)
            }
        }
    }

    override suspend fun getAllUsersDetails(): Flow<List<UserDetailsEntity>> = userQueries.selectAllUsers()
        .asFlow()
        .flowOn(readDispatcher.value)
        .mapToList()
        .map { entryList -> entryList.map(mapper::toDetailsModel) }

    override suspend fun observeUserDetailsByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserDetailsEntity?> =
        userCache.get(qualifiedID) {
            userQueries.selectDetailsByQualifiedId(listOf(qualifiedID))
                .asFlow()
                .mapToOneOrNull()
                .map { it?.let { mapper.toDetailsModel(it) } }
        }

    override suspend fun getUserDetailsWithTeamByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<Pair<UserDetailsEntity, TeamEntity?>?> =
        userQueries.selectWithTeamByQualifiedId(listOf(qualifiedID), mapper::toUserAndTeamPairModel)
            .asFlow()
            .mapToOneOrNull()

    override suspend fun getUserMinimizedByQualifiedID(qualifiedID: QualifiedIDEntity): UserEntityMinimized? =
        withContext(readDispatcher.value) {
            userQueries.selectMinimizedByQualifiedId(listOf(qualifiedID)) { qualifiedId, name, completeAssetId, userType, accentId ->
                mapper.toModelMinimized(qualifiedId, name, completeAssetId, userType, accentId)
            }.executeAsOneOrNull()
        }

    override suspend fun getUsersMinimizedByQualifiedIDs(qualifiedIDs: List<QualifiedIDEntity>): List<UserEntityMinimized> =
        withContext(readDispatcher.value) {
            userQueries.selectMinimizedByQualifiedId(qualifiedIDs) { qualifiedId, name, completeAssetId, userType, accentId ->
                mapper.toModelMinimized(qualifiedId, name, completeAssetId, userType, accentId)
            }.executeAsList()
        }

    override suspend fun getUserDetailsByQualifiedID(qualifiedID: QualifiedIDEntity): UserDetailsEntity? =
        withContext(readDispatcher.value) {
            userQueries.selectDetailsByQualifiedId(listOf(qualifiedID))
                .executeAsOneOrNull()
                ?.let { mapper.toDetailsModel(it) }
        }

    override suspend fun getUsersDetailsByQualifiedIDList(qualifiedIDList: List<QualifiedIDEntity>): List<UserDetailsEntity> =
        withContext(readDispatcher.value) {
            userQueries.selectDetailsByQualifiedId(qualifiedIDList)
                .executeAsList()
                .map { mapper.toDetailsModel(it) }
        }

    override suspend fun getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
        searchQuery: String,
        connectionStates: List<ConnectionEntity.State>
    ): Flow<List<UserDetailsEntity>> = userQueries.selectByNameOrHandleOrEmailAndConnectionState(searchQuery, connectionStates)
        .asFlow()
        .flowOn(readDispatcher.value)
        .mapToList()
        .map { it.map(mapper::toDetailsModel) }

    override suspend fun getUserDetailsByHandleAndConnectionStates(
        handle: String,
        connectionStates: List<ConnectionEntity.State>
    ) = userQueries.selectByHandleAndConnectionState(handle, connectionStates)
        .asFlow()
        .flowOn(readDispatcher.value)
        .mapToList()
        .map { it.map(mapper::toDetailsModel) }

    override suspend fun getUsersWithOneOnOneConversation(): List<UserEntity> = withContext(readDispatcher.value) {
        userQueries.selectUsersWithOneOnOne().executeAsList().map(mapper::toModel)
    }

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity) {
        withContext(writeDispatcher.value) {
            userQueries.deleteUser(qualifiedID)
        }
    }

    override suspend fun markUserAsDeletedAndRemoveFromGroupConv(
        qualifiedID: QualifiedIDEntity
    ): List<ConversationIDEntity> =
        withContext(writeDispatcher.value) {
            userQueries.transactionWithResult {
                val conversationIds = userQueries.selectGroupConversationsUserIsMemberOf(qualifiedID).executeAsList()
                safeMarkAsDeletedAndRemoveFromGroupConversation(qualifiedID)
                conversationIds
            }
        }

    // returns true if any row has been inserted or modified, false if exactly the same data already exists
    private fun markUserAsDeleted(qualifiedID: QualifiedIDEntity, userType: UserTypeEntity): Boolean {
        userQueries.markUserAsDeleted(qualifiedID, userType)
        return userQueries.selectChanges().executeAsOne() > 0
    }

    // returns true if any row has been inserted or modified, false if exactly the same data already exists
    private fun deleteUserFromGroupConversations(qualifiedID: QualifiedIDEntity): Boolean {
        userQueries.deleteUserFromGroupConversations(qualifiedID)
        return userQueries.selectChanges().executeAsOne() > 0
    }

    // returns true if any row has been inserted or modified, false if exactly the same data already exists
    private fun safeMarkAsDeletedAndRemoveFromGroupConversation(qualifiedID: QualifiedIDEntity): Boolean {
        return markUserAsDeleted(qualifiedID, UserTypeEntity.NONE) or deleteUserFromGroupConversations(qualifiedID)
    }

    override suspend fun markAsDeleted(userId: List<UserIDEntity>) {
        userQueries.transaction {
            userId.forEach {
                userQueries.markUserAsDeleted(it, UserTypeEntity.NONE)
            }
        }
    }

    override suspend fun markUserAsDefederated(qualifiedID: QualifiedIDEntity) {
        userQueries.markUserAsDefederated(qualifiedID)
    }

    override suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String) {
        withContext(writeDispatcher.value) {
            userQueries.updateUserhandle(handle, qualifiedID)
        }
    }

    override suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity) {
        withContext(writeDispatcher.value) {
            userQueries.updateUserAvailabilityStatus(status, qualifiedID)
        }
    }

    override fun observeUsersDetailsNotInConversation(conversationId: QualifiedIDEntity): Flow<List<UserDetailsEntity>> =
        userQueries.getUsersNotPartOfTheConversation(conversationId)
            .asFlow()
            .flowOn(readDispatcher.value)
            .mapToList()
            .map { it.map(mapper::toDetailsModel) }

    override suspend fun getUsersDetailsNotInConversationByNameOrHandleOrEmail(
        conversationId: QualifiedIDEntity,
        searchQuery: String
    ): Flow<List<UserDetailsEntity>> =
        userQueries.getUsersNotInConversationByNameOrHandleOrEmail(conversationId, searchQuery)
            .asFlow()
            .flowOn(readDispatcher.value)
            .mapToList()
            .map { it.map(mapper::toDetailsModel) }

    override suspend fun getUsersDetailsNotInConversationByHandle(
        conversationId: QualifiedIDEntity,
        handle: String
    ): Flow<List<UserDetailsEntity>> =
        userQueries.getUsersNotInConversationByHandle(conversationId, handle)
            .asFlow()
            .flowOn(readDispatcher.value)
            .mapToList()
            .map { it.map(mapper::toDetailsModel) }

    override suspend fun insertOrIgnoreIncompleteUsers(userIds: List<QualifiedIDEntity>) =
        withContext(writeDispatcher.value) {
            userQueries.transaction {
                for (userId: QualifiedIDEntity in userIds) {
                    userQueries.insertOrIgnoreUserId(userId)
                }
            }
        }

    override suspend fun insertOrIgnoreIncompleteUserWithOnlyEmail(userId: QualifiedIDEntity, email: String) {
        withContext(writeDispatcher.value) {
            userQueries.insertOrIgnoreUserIdWithEmail(userId, email)
        }
    }

    override suspend fun observeAllUsersDetailsByConnectionStatus(connectionState: ConnectionEntity.State): Flow<List<UserDetailsEntity>> =
        userQueries.selectAllUsersWithConnectionStatus(connectionState)
            .asFlow()
            .flowOn(readDispatcher.value)
            .mapToList()
            .map { it.map(mapper::toDetailsModel) }

    override suspend fun getAllUsersDetailsByTeam(teamId: String): List<UserDetailsEntity> = withContext(readDispatcher.value) {
        userQueries.selectUsersByTeam(teamId)
            .executeAsList()
            .map(mapper::toDetailsModel)
    }

    override suspend fun updateUserDisplayName(selfUserId: QualifiedIDEntity, displayName: String) {
        withContext(writeDispatcher.value) {
            userQueries.updateUserDisplayName(displayName, selfUserId)
        }
    }

    override suspend fun updateUserAccentColor(selfUserId: QualifiedIDEntity, accentId: Int) {
        userQueries.updateUserAccentColor(accentId, selfUserId)
    }

    override suspend fun removeUserAsset(assetId: QualifiedIDEntity) {
        userQueries.updateUserAsset(null, null, assetId)
    }

    override suspend fun getUsersDetailsWithoutMetadata() = withContext(readDispatcher.value) {
        userQueries.selectUsersWithoutMetadata()
            .executeAsList()
            .map(mapper::toDetailsModel)
    }

    override suspend fun allOtherUsersId(): List<UserIDEntity> = withContext(readDispatcher.value) {
        userQueries.userIdsWithoutSelf().executeAsList()
    }

    override suspend fun updateUserSupportedProtocols(selfUserId: QualifiedIDEntity, supportedProtocols: Set<SupportedProtocolEntity>) {
        withContext(writeDispatcher.value) {
            userQueries.updateUserSupportedProtocols(supportedProtocols, selfUserId)
        }
    }

    override suspend fun updateActiveOneOnOneConversation(userId: QualifiedIDEntity, conversationId: QualifiedIDEntity) {
        withContext(writeDispatcher.value) {
            userQueries.updateOneOnOnConversationId(conversationId, userId)
        }
    }

    override suspend fun updateActiveOneOnOneConversationIfNotSet(
        userId: QualifiedIDEntity,
        conversationId: QualifiedIDEntity,
    ) {
        withContext(writeDispatcher.value) {
            userQueries.setOneOnOneConversationIdIfNotSet(conversationId, userId)
        }
    }

    override suspend fun upsertConnectionStatuses(userStatuses: Map<QualifiedIDEntity, ConnectionEntity.State>) {
        withContext(writeDispatcher.value) {
            userQueries.transaction {
                for (user: Map.Entry<QualifiedIDEntity, ConnectionEntity.State> in userStatuses) {
                    userQueries.upsertUserConnectionStatus(user.key, user.value)
                }
            }
        }
    }

    override suspend fun isAtLeastOneUserATeamMember(userId: List<UserIDEntity>, teamId: String): Boolean =
        withContext(readDispatcher.value) {
            userQueries.isOneUserATeamMember(userId, teamId).executeAsOneOrNull() ?: false
        }

    override suspend fun getOneOnOnConversationId(userId: UserIDEntity): QualifiedIDEntity? = withContext(readDispatcher.value) {
        userQueries.selectOneOnOnConversationId(userId).executeAsOneOrNull()?.active_one_on_one_conversation_id
    }

    override suspend fun getNameAndHandle(userId: UserIDEntity): NameAndHandleEntity? = withContext(readDispatcher.value) {
        userQueries.selectNamesAndHandle(userId, ::NameAndHandleEntity).executeAsOneOrNull()
    }

    override suspend fun updateTeamId(userId: UserIDEntity, teamId: String) {
        userQueries.updateTeamId(teamId, userId)
    }

    override suspend fun countContactsAmount(selfUserId: QualifiedIDEntity): Int = withContext(readDispatcher.value) {
        userQueries.countContacts(selfUserId).executeAsOneOrNull()?.toInt() ?: 0
    }

    override suspend fun countTeamMembersAmount(teamId: String): Int = withContext(readDispatcher.value) {
        userQueries.countTeamMembersFromTeam(teamId).executeAsOneOrNull()?.toInt() ?: 0
    }

}
