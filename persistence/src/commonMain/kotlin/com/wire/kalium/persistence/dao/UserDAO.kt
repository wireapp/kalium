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

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.persistence.dao.ManagedByEntity.WIRE
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedIDEntity(
    @SerialName("value") val value: String,
    @SerialName("domain") val domain: String
) {
    override fun toString(): String = if (domain.isEmpty()) value else "$value${VALUE_DOMAIN_SEPARATOR}$domain"

    fun toLogString(): String = if (domain.isEmpty()) {
        value.obfuscateId()
    } else {
        "${value.obfuscateId()}${VALUE_DOMAIN_SEPARATOR}${domain.obfuscateDomain()}"
    }

    companion object {
        private const val VALUE_DOMAIN_SEPARATOR = '@'
    }

}

typealias UserIDEntity = QualifiedIDEntity
typealias ConversationIDEntity = QualifiedIDEntity

@Serializable
enum class SupportedProtocolEntity {
    @SerialName("PROTEUS") PROTEUS,
    @SerialName("MLS") MLS
}

enum class UserAvailabilityStatusEntity {
    NONE, AVAILABLE, BUSY, AWAY
}

data class UserEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?,
    val connectionStatus: ConnectionEntity.State = ConnectionEntity.State.NOT_CONNECTED,
    val previewAssetId: UserAssetIdEntity?,
    val completeAssetId: UserAssetIdEntity?,
    // for now availabilityStatus is stored only locally and ignored for API models,
    // later, when API start supporting it, it should be added into API model too
    val availabilityStatus: UserAvailabilityStatusEntity,
    val userType: UserTypeEntity,
    val botService: BotIdEntity?,
    val deleted: Boolean,
    val hasIncompleteMetadata: Boolean = false,
    val expiresAt: Instant?,
    val defederated: Boolean,
    val supportedProtocols: Set<SupportedProtocolEntity>?,
    val activeOneOnOneConversationId: QualifiedIDEntity?
)

data class UserDetailsEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?,
    val connectionStatus: ConnectionEntity.State = ConnectionEntity.State.NOT_CONNECTED,
    val previewAssetId: UserAssetIdEntity?,
    val completeAssetId: UserAssetIdEntity?,
    // for now availabilityStatus is stored only locally and ignored for API models,
    // later, when API start supporting it, it should be added into API model too
    val availabilityStatus: UserAvailabilityStatusEntity,
    val userType: UserTypeEntity,
    val botService: BotIdEntity?,
    val deleted: Boolean,
    val hasIncompleteMetadata: Boolean = false,
    val expiresAt: Instant?,
    val defederated: Boolean,
    val isProteusVerified: Boolean,
    val supportedProtocols: Set<SupportedProtocolEntity>?,
    val activeOneOnOneConversationId: QualifiedIDEntity?,
    val isUnderLegalHold: Boolean,
) {
    fun toSimpleEntity() = UserEntity(
        id = id,
        name = name,
        handle = handle,
        email = email,
        phone = phone,
        accentId = accentId,
        team = team,
        connectionStatus = connectionStatus,
        previewAssetId = previewAssetId,
        completeAssetId = completeAssetId,
        availabilityStatus = availabilityStatus,
        userType = userType,
        botService = botService,
        deleted = deleted,
        hasIncompleteMetadata = hasIncompleteMetadata,
        expiresAt = expiresAt,
        defederated = defederated,
        supportedProtocols = supportedProtocols,
        activeOneOnOneConversationId = activeOneOnOneConversationId
    )
}

data class UserEntityMinimized(
    val id: QualifiedIDEntity,
    val name: String?,
    val completeAssetId: UserAssetIdEntity?,
    val userType: UserTypeEntity,
)

data class BotIdEntity(
    val id: String,
    val provider: String
)

data class PartialUserEntity(
    val id: QualifiedIDEntity,
    val name: String? = null,
    val handle: String? = null,
    val email: String? = null,
    val accentId: Int? = null,
    val previewAssetId: UserAssetIdEntity? = null,
    val completeAssetId: UserAssetIdEntity? = null,
    val supportedProtocols: Set<SupportedProtocolEntity>? = null
)

enum class UserTypeEntity {

    /**Team member with owner permissions */
    OWNER,

    /**Team member with admin permissions */
    ADMIN,

    /**Team member */
    STANDARD,

    /**Team member with limited permissions */
    EXTERNAL,

    /**
     * A user on the same backend but not on your team or,
     * Any user on another backend using the Wire application,
     */
    FEDERATED,

    /**
     * Any user in wire.com using the Wire application or,
     * A temporary user that joined using the guest web interface,
     * from inside the backend network or,
     * A temporary user that joined using the guest web interface,
     * from outside the backend network
     */
    GUEST,

    /** Service bot */
    SERVICE,

    /**
     * A user on the same backend,
     * when current user doesn't belongs to any team
     */
    NONE;
}

/**
 * This is used to indicate if the self user (account) is managed by SCIM or Wire
 * If the user is managed by other than [WIRE], then is a read only account.
 */
enum class ManagedByEntity {
    WIRE, SCIM
}

internal typealias UserAssetIdEntity = QualifiedIDEntity

@Suppress("TooManyFunctions")
interface UserDAO {
    /**
     * Inserts each user into the local storage or ignores if already exists
     */
    suspend fun insertOrIgnoreUsers(users: List<UserEntity>)

    /**
     * Perform a partial update of an existing user. Only non-null values will be updated otherwise
     * the existing value is kept.
     *
     * @return true if the user was updated
     */
    suspend fun updateUser(update: PartialUserEntity)

    suspend fun updateUser(users: List<PartialUserEntity>)

    /**
     * This will update all columns (or insert a new record), except:
     * - [ConnectionEntity.State]
     * - [UserAvailabilityStatusEntity]
     * - [UserEntity.activeOneOnOneConversationId]
     * - [UserEntity.defederated] (this will be set to false)
     *
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case as the transaction can be executed many times, we need to take care for not deleting old data.
     */
    suspend fun upsertUser(user: UserEntity)

    /**
     * This will update all columns (or insert a new record), except:
     * - [ConnectionEntity.State]
     * - [UserAvailabilityStatusEntity]
     * - [UserEntity.activeOneOnOneConversationId]
     * - [UserEntity.defederated] (this will be set to false)
     *
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case as the transaction can be executed many times, we need to take care for not deleting old data.
     */
    suspend fun upsertUsers(users: List<UserEntity>)

    /**
     * This will update [UserEntity.team], [UserEntity.userType], [UserEntity.connectionStatus] to [ConnectionEntity.State.ACCEPTED]
     * or insert a new record.
     *
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case when trying to insert a member, we could already have the record, so we need to pass only the data needed.
     */
    suspend fun upsertTeamMemberUserTypes(users: Map<QualifiedIDEntity, UserTypeEntity>)
    suspend fun getAllUsersDetails(): Flow<List<UserDetailsEntity>>
    suspend fun observeAllUsersDetailsByConnectionStatus(connectionState: ConnectionEntity.State): Flow<List<UserDetailsEntity>>
    suspend fun observeUserDetailsByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserDetailsEntity?>
    suspend fun getUserDetailsWithTeamByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<Pair<UserDetailsEntity, TeamEntity?>?>
    suspend fun getUserMinimizedByQualifiedID(qualifiedID: QualifiedIDEntity): UserEntityMinimized?
    suspend fun getUsersDetailsByQualifiedIDList(qualifiedIDList: List<QualifiedIDEntity>): List<UserDetailsEntity>
    suspend fun getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
        searchQuery: String,
        connectionStates: List<ConnectionEntity.State>
    ): Flow<List<UserDetailsEntity>>

    suspend fun getUserDetailsByHandleAndConnectionStates(
        handle: String,
        connectionStates: List<ConnectionEntity.State>
    ): Flow<List<UserDetailsEntity>>

    suspend fun getUsersWithOneOnOneConversation(): List<UserEntity>

    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun markUserAsDeletedAndRemoveFromGroupConv(qualifiedID: QualifiedIDEntity): List<ConversationIDEntity>
    suspend fun markAsDeleted(userId: List<UserIDEntity>)
    suspend fun markUserAsDefederated(qualifiedID: QualifiedIDEntity)
    suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String)
    suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity)
    fun observeUsersDetailsNotInConversation(conversationId: QualifiedIDEntity): Flow<List<UserDetailsEntity>>
    suspend fun insertOrIgnoreIncompleteUsers(userIds: List<QualifiedIDEntity>)
    suspend fun getUsersDetailsNotInConversationByNameOrHandleOrEmail(
        conversationId: QualifiedIDEntity,
        searchQuery: String,
    ): Flow<List<UserDetailsEntity>>

    suspend fun getUsersDetailsNotInConversationByHandle(conversationId: QualifiedIDEntity, handle: String): Flow<List<UserDetailsEntity>>
    suspend fun getAllUsersDetailsByTeam(teamId: String): List<UserDetailsEntity>
    suspend fun updateUserDisplayName(selfUserId: QualifiedIDEntity, displayName: String)

    suspend fun removeUserAsset(assetId: QualifiedIDEntity)

    suspend fun getUsersDetailsWithoutMetadata(): List<UserDetailsEntity>

    /**
     * @return [List] of [UserIDEntity] of all other users.
     * the list does not contain self user ID
     */
    suspend fun allOtherUsersId(): List<UserIDEntity>

    suspend fun updateUserSupportedProtocols(selfUserId: QualifiedIDEntity, supportedProtocols: Set<SupportedProtocolEntity>)

    /**
     * Update which 1-1 conversation is the currently active one. If multiple encryption protocols are enabled
     * there can be multiple co-existing 1-1 conversations.
     */
    suspend fun updateActiveOneOnOneConversation(userId: QualifiedIDEntity, conversationId: QualifiedIDEntity)

    suspend fun upsertConnectionStatuses(userStatuses: Map<QualifiedIDEntity, ConnectionEntity.State>)
    suspend fun isAtLeastOneUserATeamMember(userId: List<UserIDEntity>, teamId: String): Boolean
    suspend fun getOneOnOnConversationId(userId: UserIDEntity): QualifiedIDEntity?
    suspend fun getUsersMinimizedByQualifiedIDs(qualifiedIDs: List<QualifiedIDEntity>): List<UserEntityMinimized>
}
