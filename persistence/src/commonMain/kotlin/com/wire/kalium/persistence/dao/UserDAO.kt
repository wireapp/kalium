package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class QualifiedID(
    val value: String,
    val domain: String
)

data class User(
    val id: QualifiedID,
    val name: String?,
    val handle: String?
) { }

interface UserDAO {
    suspend fun insertUser(user: User)
    suspend fun insertUsers(users: List<User>)
    suspend fun updateUser(user: User)
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedID): Flow<User?>
    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedID)
}
