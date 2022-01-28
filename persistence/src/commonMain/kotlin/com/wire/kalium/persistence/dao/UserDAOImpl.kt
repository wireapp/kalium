package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.db.UsersQueries
import kotlinx.coroutines.flow.Flow
import com.squareup.sqldelight.runtime.coroutines.*
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.User as SQLDelightUser

class UserMapper {
    fun toModel(user: SQLDelightUser): User {
        return User(user.qualified_id, user.name, user.handle)
    }
}

class UserDAOImpl(private val queries: UsersQueries): UserDAO {

    val mapper = UserMapper()

    override suspend fun insertUser(user: User) {
        queries.insertUser(user.id, user.name, user.handle)
    }

    override suspend fun insertUsers(users: List<User>) {
        queries.transaction {
            for (user: User in users) {
                queries.insertUser(user.id, user.name, user.handle)
            }
        }
    }

    override suspend fun updateUser(user: User) {
        queries.updateUser(user.name, user.handle, user.id)
    }

    override suspend fun getUserByQualifiedID(qualifiedID: QualifiedID): Flow<User?> {
        return queries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { mapper.toModel(it) } }
    }

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedID) {
        queries.deleteUser(qualifiedID)
    }

}
