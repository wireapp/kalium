package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedIDEntity(
    val value: String,
    val domain: String
)

typealias UserIDEntity = QualifiedIDEntity
typealias ConversationIDEntity = QualifiedIDEntity

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
    val availabilityStatus: UserAvailabilityStatusEntity
)

internal typealias UserAssetIdEntity = String

interface UserDAO {
    /**
     * Inserts a new user into the local storage
     */
    suspend fun insertUser(user: UserEntity)

    /**
     * This will update all columns, except [ConnectionState] or insert a new record with default value [ConnectionState.NOT_CONNECTED]
     *
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case as the transaction can be executed many times, we need to take care for not deleting old data.
     */
    suspend fun upsertUsers(users: List<UserEntity>)

    /**
     * This will update [UserEntity.team] and [UserEntity.connectionStatus] to [ConnectionState.ACCEPTED]
     * or insert a new record with default values for other columns.
     *
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case when trying to insert a member, we could already have the record, so we need to pass only the data needed.
     */
    suspend fun upsertTeamMembers(users: List<UserEntity>)

    /**
     * This will update a user record corresponding to the Self User,
     * The Fields to update are:
     * [UserEntity.name]
     * [UserEntity.handle]
     * [UserEntity.email]
     * [UserEntity.accentId]
     * [UserEntity.previewAssetId]
     * [UserEntity.completeAssetId]
     */
    suspend fun updateSelfUser(user: UserEntity)
    suspend fun getAllUsers(): Flow<List<UserEntity>>
    suspend fun getAllUsersByConnectionStatus(connectionState: ConnectionEntity.State): List<UserEntity>
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?>
    suspend fun getUserByNameOrHandleOrEmailAndConnectionState(
        searchQuery: String,
        connectionState: ConnectionEntity.State
    ): List<UserEntity>

    suspend fun getUserByHandleAndConnectionState(
        handle: String,
        connectionState: ConnectionEntity.State
    ): List<UserEntity>

    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String)
    suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity)
}
