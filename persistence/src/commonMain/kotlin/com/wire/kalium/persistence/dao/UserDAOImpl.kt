package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.UsersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.User as SQLDelightUser

class UserMapper {
    fun toModel(user: SQLDelightUser): UserEntity {
        return UserEntity(
            id = user.qualified_id,
            name = user.name,
            handle = user.handle,
            email = user.email,
            phone = user.phone,
            accentId = user.accent_id,
            team = user.team,
            connectionStatus = user.connection_status,
            previewAssetId = user.preview_asset_id,
            completeAssetId = user.complete_asset_id
        )
    }
}

class UserDAOImpl(private val queries: UsersQueries) : UserDAO {

    val mapper = UserMapper()

    override fun insertUser(user: UserEntity) {
        queries.insertUser(
            user.id,
            user.name,
            user.handle,
            user.email,
            user.phone,
            user.accentId,
            user.team,
            user.connectionStatus,
            user.previewAssetId,
            user.completeAssetId
        )
    }

    override fun insertUsers(users: List<UserEntity>) {
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
                    user.connectionStatus,
                    user.previewAssetId,
                    user.completeAssetId
                )
            }
        }
    }

    override fun updateUser(user: UserEntity) =
        queries.updateUser(user.name, user.handle, user.email, user.accentId, user.previewAssetId, user.completeAssetId, user.id)


    override fun getAllUsersFlow(): Flow<List<UserEntity>> = queries.selectAllUsers()
        .asFlow()
        .mapToList()
        .map { entryList -> entryList.map(mapper::toModel) }

    override fun getAllUsers(): List<UserEntity> =
        queries.selectAllUsers().executeAsList().map(mapper::toModel)

    override fun getUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<UserEntity?> {
        return queries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { mapper.toModel(it) } }
    }

    override fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): UserEntity? =
        queries.selectByQualifiedId(qualifiedID).executeAsOneOrNull()?.let { mapper.toModel(it) }

    override fun getUserByNameOrHandleOrEmailFlow(searchQuery: String): Flow<List<UserEntity>> {
        return queries.selectByNameOrHandleOrEmail(searchQuery)
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }
    }

    override fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity) {
        queries.deleteUser(qualifiedID)
    }

    override fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String) {
        queries.updateUserhandle(handle, qualifiedID)
    }
}
