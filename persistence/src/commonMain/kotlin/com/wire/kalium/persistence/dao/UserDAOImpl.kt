package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.db.User as SQLDelightUser
import com.wire.kalium.persistence.db.UsersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserMapper {
    fun toModel(user: SQLDelightUser): User {
        return User(user.qualified_id, user.name, user.handle, user.email, user.phone, user.accent_id?.toInt()!!, user.team) //TODO: pending investigate int issue
    }
}

class UserDAOImpl(private val queries: UsersQueries) : UserDAO {

    val mapper = UserMapper()

    override suspend fun insertUser(user: User) {
        queries.insertUser(user.id, user.name, user.handle, user.email, user.phone, user.accentId.toString(), user.team)
    }

    override suspend fun insertUsers(users: List<User>) {
        queries.transaction {
            for (user: User in users) {
                queries.insertUser(user.id, user.name, user.handle, user.email, user.phone, user.accentId.toString(), user.team)
            }
        }
    }

    override suspend fun updateUser(user: User) {
        queries.updateUser(user.name, user.handle, user.email, user.accentId.toString(), user.id)
    }

    override suspend fun getAllUsers(): Flow<List<User>> = queries.selectAllUsers()
        .asFlow()
        .mapToList()
        .map { entryList -> entryList.map(mapper::toModel) }

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
