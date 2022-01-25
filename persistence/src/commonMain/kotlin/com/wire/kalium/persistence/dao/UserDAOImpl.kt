package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.db.UsersQueries
import kotlinx.coroutines.flow.Flow
import com.squareup.sqldelight.runtime.coroutines.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.User as SQLDelightUser

class UserMapper {
    fun toDAO(user: SQLDelightUser): User {
        return User(user.qualified_id, user.name, user.handle)
    }
}

class QualifiedIDAdapter: ColumnAdapter<QualifiedID, String> {

    override fun decode(databaseValue: String): QualifiedID {
        val components = databaseValue.split("@")
        return QualifiedID(components.first(), components.last())
    }

    override fun encode(value: QualifiedID): String {
        return "${value.value}@${value.domain}"
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
            .map { it?.let { mapper.toDAO(it) } }
    }

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedID) {
        queries.deleteUser(qualifiedID)
    }

}
