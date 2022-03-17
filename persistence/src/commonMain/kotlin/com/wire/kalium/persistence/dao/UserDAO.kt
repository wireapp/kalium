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
    val previewAssetId: UserAssetIdEntity?,
    val completeAssetId: UserAssetIdEntity?
)
internal typealias UserAssetIdEntity = String

interface UserDAO {
    suspend fun insertUser(user: UserEntity)
    suspend fun insertUsers(users: List<UserEntity>)
    suspend fun updateUser(user: UserEntity)
    suspend fun getAllUsers(): Flow<List<UserEntity>>
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?>
    suspend fun getUserByNameOrHandleOrEmail(searchQuery: String): Flow<List<UserEntity>>
    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity)
}
