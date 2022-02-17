package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedID(
    val value: String,
    val domain: String
)

typealias UserId = QualifiedID

data class User(
    val id: QualifiedID,
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?
    // val picture: List<UserAsset>
)

interface UserDAO {
    suspend fun insertUser(user: User)
    suspend fun insertUsers(users: List<User>)
    suspend fun updateUser(user: User)
    suspend fun getAllUsers(): Flow<List<User>>
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedID): Flow<User?>
    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedID)
}
