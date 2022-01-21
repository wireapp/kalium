package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

public data class QualifiedID(
    val value: String,
    val domain: String
)

public data class User(
    public val id: QualifiedID,
    public val name: String?,
    public val handle: String?
) { }

public interface UserDAO {
    suspend fun insertUser(user: User)
    suspend fun insertUsers(users: List<User>)
    suspend fun updateUser(user: User)
    suspend fun getUserByQualifiedID(qualifiedID: QualifiedID): Flow<User?>
    suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedID)
}
