package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedIDEntity(
    @SerialName("value") val value: String,
    @SerialName("domain") val domain: String
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
    val availabilityStatus: UserAvailabilityStatusEntity,
    val userType: UserTypeEntity,
    val botService: BotEntity?,
    val deleted: Boolean
)

data class UserEntityMinimized(
    val id: QualifiedIDEntity,
    val name: String?,
    val completeAssetId: UserAssetIdEntity?,
    val userType: UserTypeEntity,
)

data class BotEntity(
    val id: String,
    val provider: String
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

internal typealias UserAssetIdEntity = QualifiedIDEntity

@Suppress("TooManyFunctions")
interface UserDAO {
    /**
     * Inserts a new user into the local storage
     */
    suspend fun insertUser(user: UserEntity)

    /**
     * This will update all columns, except [ConnectionEntity.State] or insert a new record with default value
     * [ConnectionEntity.State.NOT_CONNECTED]
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case as the transaction can be executed many times, we need to take care for not deleting old data.
     */
    suspend fun upsertUsers(users: List<UserEntity>)

    /**
     * This will update [UserEntity.team], [UserEntity.userType], [UserEntity.connectionStatus] to [ConnectionEntity.State.ACCEPTED]
     * or insert a new record with default values for other columns.
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case when trying to insert a member, we could already have the record, so we need to pass only the data needed.
     */
    suspend fun upsertTeamMembersTypes(users: List<UserEntity>)

    /**
     * This will update all columns, except [UserEntity.userType] or insert a new record with default values
     * An upsert operation is a one that tries to update a record and if fails (not rows affected by change) inserts instead.
     * In this case as the transaction can be executed many times, we need to take care for not deleting old data.
     */
    suspend fun upsertTeamMembers(users: List<UserEntity>)

    /**
     * This will update a user record corresponding to the User,
     * The Fields to update are:
     * [UserEntity.name]
     * [UserEntity.handle]
     * [UserEntity.email]
     * [UserEntity.accentId]
     * [UserEntity.previewAssetId]
     * [UserEntity.completeAssetId]
     */
    suspend fun updateUser(user: UserEntity)
    suspend fun getAllUsers(): Flow<List<UserEntity>>
    suspend fun getAllUsersByConnectionStatus(connectionState: ConnectionEntity.State): List<UserEntity>
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?>
    fun getUserMinimizedByQualifiedID(qualifiedID: QualifiedIDEntity): UserEntityMinimized?
    suspend fun getUsersByQualifiedIDList(qualifiedIDList: List<QualifiedIDEntity>): List<UserEntity>
    suspend fun getUserByNameOrHandleOrEmailAndConnectionStates(
        searchQuery: String,
        connectionStates: List<ConnectionEntity.State>
    ): Flow<List<UserEntity>>

    suspend fun getUserByHandleAndConnectionStates(
        handle: String,
        connectionStates: List<ConnectionEntity.State>
    ): Flow<List<UserEntity>>

    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String)
    suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity)
    suspend fun getUsersNotInConversation(conversationId: QualifiedIDEntity): List<UserEntity>
    suspend fun insertOrIgnoreUserWithConnectionStatus(qualifiedID: QualifiedIDEntity, connectionStatus: ConnectionEntity.State)
    suspend fun getUsersNotInConversationByNameOrHandleOrEmail(
        conversationId: QualifiedIDEntity,
        searchQuery: String,
    ): Flow<List<UserEntity>>

    suspend fun getUsersNotInConversationByHandle(conversationId: QualifiedIDEntity, handle: String): Flow<List<UserEntity>>
    suspend fun getAllUsersByTeam(teamId: String): List<UserEntity>
}
