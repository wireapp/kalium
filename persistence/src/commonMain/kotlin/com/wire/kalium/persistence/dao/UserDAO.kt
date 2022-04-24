package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedIDEntity(
    val value: String,
    val domain: String
)

typealias UserIDEntity = QualifiedIDEntity

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
    fun insertUser(user: UserEntity)
    fun insertUsers(users: List<UserEntity>)
    fun updateUser(user: UserEntity)
    fun getAllUsersFlow(): Flow<List<UserEntity>>
    fun getAllUsers(): List<UserEntity>
    fun getUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<UserEntity?>
    fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): UserEntity?
    fun getUserByNameOrHandleOrEmailFlow(searchQuery: String): Flow<List<UserEntity>>
    fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String)
}
