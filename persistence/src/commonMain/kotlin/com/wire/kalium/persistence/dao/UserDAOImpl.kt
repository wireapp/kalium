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

    override suspend fun insertUser(user: UserEntity) {
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
                    user.connectionStatus,
                    user.previewAssetId,
                    user.completeAssetId
                )
            }
        }
    }

    override suspend fun updateUser(user: UserEntity) {
        queries.updateUser(user.name, user.handle, user.email, user.accentId, user.previewAssetId, user.completeAssetId, user.id)
    }

    override suspend fun updateUsers(users: List<UserEntity>) {
        queries.transaction {
            users.forEach { user ->
                queries.updateUser(user.name, user.handle, user.email, user.accentId, user.previewAssetId, user.completeAssetId, user.id)
                val recordDidNotExist = queries.selectChanges().executeAsOne() == 0L
                if (recordDidNotExist) {
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
    }

    override suspend fun getAllUsers(): Flow<List<UserEntity>> = queries.selectAllUsers()
        .asFlow()
        .mapToList()
        .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?> {
        return queries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { mapper.toModel(it) } }
    }

    override suspend fun getUserByNameOrHandleOrEmailAndConnectionState(
        searchQuery: String,
        connectionState: UserEntity.ConnectionState
    ) = queries.selectByNameOrHandleOrEmailAndConnectionState(searchQuery, connectionState)
        .executeAsList()
        .map(mapper::toModel)

    override suspend fun getUserByHandleAndConnectionState(
        handle: String,
        connectionState: UserEntity.ConnectionState
    ) = queries.selectByHandleAndConnectionState(handle, connectionState)
        .executeAsList()
        .map(mapper::toModel)

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity) {
        queries.deleteUser(qualifiedID)
    }

    override suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String) {
        queries.updateUserhandle(handle, qualifiedID)
    }

    override suspend fun getAllUsersByConnectionStatus(connectionState: UserEntity.ConnectionState): List<UserEntity> =
        queries.selectAllUsersWithConnectionStatus(connectionState)
            .executeAsList()
            .map(mapper::toModel)
}
