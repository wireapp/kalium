package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.db.UsersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.User as SQLDelightUser

class UserMapper {
    fun toModel(user: SQLDelightUser): UserEntity {
        return UserEntity(
            user.qualified_id,
            user.name,
            user.handle,
            user.email,
            user.phone,
            user.accent_id,
            user.team,
            user.preview_asset_id,
            user.complete_asset_id
        )
    }
}

class UserDAOImpl(private val queries: UsersQueries) : UserDAO {

    val mapper = UserMapper()

    override suspend fun insertUser(user: UserEntity) {
        queries.insertUser(
            user.id,
            user.name,
            user.handle,
            user.email,
            user.phone,
            user.accentId,
            user.team,
            user.previewAssetId,
            user.completeAssetId
        )
    }

    override suspend fun insertUsers(users: List<UserEntity>) {
        queries.transaction {
            for (user: UserEntity in users) {
                queries.insertUser(
                    user.id,
                    user.name,
                    user.handle,
                    user.email,
                    user.phone,
                    user.accentId,
                    user.team,
                    user.previewAssetId,
                    user.completeAssetId
                )
            }
        }
    }

    override suspend fun updateUser(user: UserEntity) {
        queries.updateUser(user.name, user.handle, user.email, user.accentId, user.previewAssetId, user.completeAssetId, user.id)
    }

    override suspend fun getAllUsers(): Flow<List<UserEntity>> = queries.selectAllUsers()
        .asFlow()
        .mapToList()
        .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getUserByQualifiedID(qualifiedID: QualifiedID): Flow<UserEntity?> {
        return queries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { mapper.toModel(it) } }
    }

    override suspend fun getUserByNameOrHandleOrEmail(searchQuery: String): Flow<List<UserEntity>> {
        return queries.selectByNameOrHandleOrEmail(searchQuery)
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }
    }

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedID) {
        queries.deleteUser(qualifiedID)
    }

    override suspend fun updateUserHandle(qualifiedID: QualifiedID, handle: String) {
        queries.updateUserhandle(handle, qualifiedID)
    }
}
