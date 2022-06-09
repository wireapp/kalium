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
            completeAssetId = user.complete_asset_id,
            availabilityStatus = user.user_availability_status
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

    override suspend fun upsertTeamMembers(users: List<UserEntity>) {
        queries.transaction {
            for (user: UserEntity in users) {
                queries.updateTeamMemberUser(user.team, user.connectionStatus, user.id)
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

    override suspend fun upsertUsers(users: List<UserEntity>) {
        queries.transaction {
            for (user: UserEntity in users) {
                queries.updateUser(
                    user.name,
                    user.handle,
                    user.email,
                    user.phone,
                    user.accentId,
                    user.team,
                    user.previewAssetId,
                    user.completeAssetId,
                    user.id
                )
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

    override suspend fun updateSelfUser(user: UserEntity) {
        queries.updateSelfUser(user.name, user.handle, user.email, user.accentId, user.previewAssetId, user.completeAssetId, user.id)
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
        connectionState: ConnectionEntity.State
    ) = queries.selectByNameOrHandleOrEmailAndConnectionState(searchQuery, connectionState)
        .executeAsList()
        .map(mapper::toModel)

    override suspend fun getUserByHandleAndConnectionState(
        handle: String,
        connectionState: ConnectionEntity.State
    ) = queries.selectByHandleAndConnectionState(handle, connectionState)
        .executeAsList()
        .map(mapper::toModel)

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity) {
        queries.deleteUser(qualifiedID)
    }

    override suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String) {
        queries.updateUserhandle(handle, qualifiedID)
    }

    override suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity) {
        queries.updateUserAvailabilityStatus(status, qualifiedID)
    }

    override suspend fun getAllUsersByConnectionStatus(connectionState: ConnectionEntity.State): List<UserEntity> =
        queries.selectAllUsersWithConnectionStatus(connectionState)
            .executeAsList()
            .map(mapper::toModel)
}
