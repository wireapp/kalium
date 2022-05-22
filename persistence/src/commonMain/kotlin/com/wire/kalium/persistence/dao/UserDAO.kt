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

data class UserEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?,
    val connectionStatus: ConnectionState = ConnectionState.NOT_CONNECTED,
    val previewAssetId: UserAssetIdEntity?,
    val completeAssetId: UserAssetIdEntity?
) {

    data class Connection (
        val conversationId: String,
        val from: String,
        val lastUpdate: String,
        val qualifiedConversationId: ConversationIDEntity,
        val qualifiedToId: QualifiedIDEntity,
        val status: ConnectionState,
        val toId: String
    )
    
    enum class ConnectionState {
        /** Default - No connection state */
        NOT_CONNECTED,

        /** The other user has sent a connection request to this one */
        PENDING,

        /** This user has sent a connection request to another user */
        SENT,

        /** The user has been blocked */
        BLOCKED,

        /** The connection has been ignored */
        IGNORED,

        /** The connection has been cancelled */
        CANCELLED,

        /** The connection is missing legal hold consent */
        MISSING_LEGALHOLD_CONSENT,

        /** The connection is complete and the conversation is in its normal state */
        ACCEPTED
    }
}

internal typealias UserAssetIdEntity = String

interface UserDAO {
    suspend fun insertUser(user: UserEntity)
    suspend fun insertUsers(users: List<UserEntity>)
    suspend fun updateUser(user: UserEntity)
    suspend fun updateUsers(users: List<UserEntity>)
    suspend fun getAllUsers(): Flow<List<UserEntity>>
    suspend fun getAllUsersByConnectionStatus(connectionState: UserEntity.ConnectionState): List<UserEntity>
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?>
    suspend fun getUserByNameOrHandleOrEmailAndConnectionState(
        searchQuery: String,
        connectionState: UserEntity.ConnectionState
    ): List<UserEntity>

    suspend fun getUserByHandleAndConnectionState(
        handle: String,
        connectionState: UserEntity.ConnectionState
    ) : List<UserEntity>

    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String)
}
