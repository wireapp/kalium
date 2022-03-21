package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedID(
    val value: String,
    val domain: String
)

typealias UserId = QualifiedID

data class UserEntity(
    val id: QualifiedID,
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?,
    val previewAssetId: UserAssetId?,
    val completeAssetId: UserAssetId?
)
internal typealias UserAssetId = String

interface UserDAO {
    suspend fun insertUser(user: UserEntity)
    suspend fun insertUsers(users: List<UserEntity>)
    suspend fun updateUser(user: UserEntity)
    suspend fun getAllUsers(): Flow<List<UserEntity>>
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedID): Flow<UserEntity?>
    suspend fun getUserByNameOrHandleOrEmail(searchQuery: String): Flow<List<UserEntity>>
    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedID)
    suspend fun updateUserHandle(qualifiedID: QualifiedID, handle: String)
}
