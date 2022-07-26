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
            availabilityStatus = user.user_availability_status,
            userTypEntity = user.user_type
        )
    }
}

@Suppress("TooManyFunctions")
class UserDAOImpl(
    private val userQueries: UsersQueries
) : UserDAO {

    val mapper = UserMapper()

    override suspend fun insertUser(user: UserEntity) {
        userQueries.insertUser(
            user.id,
            user.name,
            user.handle,
            user.email,
            user.phone,
            user.accentId,
            user.team,
            user.connectionStatus,
            user.previewAssetId,
            user.completeAssetId,
            user.userTypEntity
        )
    }

    override suspend fun upsertTeamMembers(users: List<UserEntity>) {
        userQueries.transaction {
            for (user: UserEntity in users) {
                userQueries.updateTeamMemberUser(user.team, user.connectionStatus, user.id)
                val recordDidNotExist = userQueries.selectChanges().executeAsOne() == 0L
                if (recordDidNotExist) {
                    userQueries.insertUser(
                        user.id,
                        user.name,
                        user.handle,
                        user.email,
                        user.phone,
                        user.accentId,
                        user.team,
                        user.connectionStatus,
                        user.previewAssetId,
                        user.completeAssetId,
                        user.userTypEntity
                    )
                }
            }
        }
    }

    override suspend fun upsertUsers(users: List<UserEntity>) {
        userQueries.transaction {
            for (user: UserEntity in users) {
                userQueries.updateUser(
                    user.name,
                    user.handle,
                    user.email,
                    user.phone,
                    user.accentId,
                    user.team,
                    user.previewAssetId,
                    user.completeAssetId,
                    user.userTypEntity,
                    user.id,
                )
                val recordDidNotExist = userQueries.selectChanges().executeAsOne() == 0L
                if (recordDidNotExist) {
                    userQueries.insertUser(
                        user.id,
                        user.name,
                        user.handle,
                        user.email,
                        user.phone,
                        user.accentId,
                        user.team,
                        user.connectionStatus,
                        user.previewAssetId,
                        user.completeAssetId,
                        user.userTypEntity
                    )
                }
            }
        }
    }

    override suspend fun updateSelfUser(user: UserEntity) {
        userQueries.updateSelfUser(user.name, user.handle, user.email, user.accentId, user.previewAssetId, user.completeAssetId, user.id)
    }

    override suspend fun getAllUsers(): Flow<List<UserEntity>> = userQueries.selectAllUsers()
        .asFlow()
        .mapToList()
        .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?> {
        return userQueries.selectByQualifiedId(listOf(qualifiedID))
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { mapper.toModel(it) } }
    }

    override suspend fun getUsersByQualifiedIDList(qualifiedIDList: List<QualifiedIDEntity>): List<UserEntity> {
        return userQueries.selectByQualifiedId(qualifiedIDList)
            .executeAsList()
            .map { mapper.toModel(it) }
    }

    override suspend fun getUserByNameOrHandleOrEmailAndConnectionState(
        searchQuery: String,
        connectionState: ConnectionEntity.State
    ) = userQueries.selectByNameOrHandleOrEmailAndConnectionState(searchQuery, connectionState)
        .executeAsList()
        .map(mapper::toModel)

    override suspend fun getUserByHandleAndConnectionState(
        handle: String,
        connectionState: ConnectionEntity.State
    ) = userQueries.selectByHandleAndConnectionState(handle, connectionState)
        .executeAsList()
        .map(mapper::toModel)

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity) {
        userQueries.deleteUser(qualifiedID)
    }

    override suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String) {
        userQueries.updateUserhandle(handle, qualifiedID)
    }

    override suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity) {
        userQueries.updateUserAvailabilityStatus(status, qualifiedID)
    }

    override suspend fun getUsersNotInConversation(conversationId: QualifiedIDEntity) : List<UserEntity> =
        userQueries.getUsersNotPartOfTheConversation(conversationId)
            .executeAsList()
            .map(mapper::toModel)

    override suspend fun getUsersNotInConversationByNameOrHandleOrEmail(
        conversationId: QualifiedIDEntity,
        searchQuery: String
    ): List<UserEntity> =
        userQueries.getUsersNotInConversationByNameOrHandleOrEmail(conversationId, searchQuery)
            .executeAsList()
            .map(mapper::toModel)

    override suspend fun getUsersNotInConversationByHandle(conversationId: QualifiedIDEntity, handle: String): List<UserEntity> =
        userQueries.getUsersNotInConversationByHandle(conversationId, handle)
            .executeAsList()
            .map(mapper::toModel)

    override suspend fun insertOrIgnoreUserWithConnectionStatus(qualifiedID: QualifiedIDEntity, connectionStatus: ConnectionEntity.State) {
        userQueries.insertOrIgnoreUserIdWithConnectionStatus(qualifiedID, connectionStatus)
    }

    override suspend fun getAllUsersByConnectionStatus(connectionState: ConnectionEntity.State): List<UserEntity> =
        userQueries.selectAllUsersWithConnectionStatus(connectionState)
            .executeAsList()
            .map(mapper::toModel)
}
