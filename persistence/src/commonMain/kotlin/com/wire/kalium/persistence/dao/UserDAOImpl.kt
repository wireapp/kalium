package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.cache.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
            userType = user.user_type,
            botService = user.bot_service,
            deleted = user.deleted
        )
    }

    fun toModelMinimized(
        userId: QualifiedIDEntity,
        name: String?,
        assetId: QualifiedIDEntity?,
        userTypeEntity: UserTypeEntity
    ) = UserEntityMinimized(
        userId,
        name,
        assetId,
        userTypeEntity
    )
}

@Suppress("TooManyFunctions")
class UserDAOImpl internal constructor(
    private val userQueries: UsersQueries,
    private val userCache: Cache<UserIDEntity, Flow<UserEntity?>>,
    private val databaseScope: CoroutineScope
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
            user.userType,
            user.botService,
            user.deleted
        )
    }

    override suspend fun insertOrIgnoreUsers(users: List<UserEntity>) {
        userQueries.transaction {
            for (user: UserEntity in users) {
                userQueries.insertOrIgnoreUser(
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
                    user.userType,
                    user.botService,
                    user.deleted
                )
            }
        }
    }

    override suspend fun upsertTeamMembers(users: List<UserEntity>) {
        userQueries.transaction {
            for (user: UserEntity in users) {
                userQueries.updateTeamMemberUser(
                    user.name,
                    user.handle,
                    user.email,
                    user.phone,
                    user.accentId,
                    user.team,
                    user.previewAssetId,
                    user.completeAssetId,
                    user.botService,
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
                        user.userType,
                        user.botService,
                        user.deleted
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
                    user.userType,
                    user.botService,
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
                        user.userType,
                        user.botService,
                        user.deleted
                    )
                }
            }
        }
    }

    override suspend fun upsertTeamMembersTypes(users: List<UserEntity>) {
        userQueries.transaction {
            for (user: UserEntity in users) {
                userQueries.updateTeamMemberType(user.team, user.connectionStatus, user.userType, user.id)
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
                        user.userType,
                        user.botService,
                        user.deleted
                    )
                }
            }
        }
    }

    override suspend fun updateUser(user: UserEntity) {
        userQueries.updateSelfUser(
            user.name,
            user.handle,
            user.email,
            user.accentId,
            user.previewAssetId,
            user.completeAssetId,
            user.id
        )
    }

    override suspend fun getAllUsers(): Flow<List<UserEntity>> = userQueries.selectAllUsers()
        .asFlow()
        .mapToList()
        .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<UserEntity?> = userCache.get(qualifiedID) {
        userQueries.selectByQualifiedId(listOf(qualifiedID))
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { mapper.toModel(it) } }
            .shareIn(databaseScope, Lazily, 1)
    }

    override fun getUserMinimizedByQualifiedID(qualifiedID: QualifiedIDEntity): UserEntityMinimized? =
        userQueries.selectMinimizedByQualifiedId(listOf(qualifiedID)) { qualifiedId, name, completeAssetId, userType ->
            mapper.toModelMinimized(qualifiedId, name, completeAssetId, userType)
        }.executeAsOneOrNull()

    override suspend fun getUsersByQualifiedIDList(qualifiedIDList: List<QualifiedIDEntity>): List<UserEntity> {
        return userQueries.selectByQualifiedId(qualifiedIDList)
            .executeAsList()
            .map { mapper.toModel(it) }
    }

    override suspend fun getUserByNameOrHandleOrEmailAndConnectionStates(
        searchQuery: String,
        connectionStates: List<ConnectionEntity.State>
    ): Flow<List<UserEntity>> = userQueries.selectByNameOrHandleOrEmailAndConnectionState(searchQuery, connectionStates)
        .asFlow()
        .mapToList()
        .map { it.map(mapper::toModel) }

    override suspend fun getUserByHandleAndConnectionStates(
        handle: String,
        connectionStates: List<ConnectionEntity.State>
    ) = userQueries.selectByHandleAndConnectionState(handle, connectionStates)
        .asFlow()
        .mapToList()
        .map { it.map(mapper::toModel) }

    override suspend fun deleteUserByQualifiedID(qualifiedID: QualifiedIDEntity) {
        userQueries.deleteUser(qualifiedID)
    }

    override suspend fun markUserAsDeleted(qualifiedID: QualifiedIDEntity) {
        userQueries.markUserAsDeleted(user_type = UserTypeEntity.NONE, qualified_id = qualifiedID)
    }

    override suspend fun updateUserHandle(qualifiedID: QualifiedIDEntity, handle: String) {
        userQueries.updateUserhandle(handle, qualifiedID)
    }

    override suspend fun updateUserAvailabilityStatus(qualifiedID: QualifiedIDEntity, status: UserAvailabilityStatusEntity) {
        userQueries.updateUserAvailabilityStatus(status, qualifiedID)
    }

    override fun observeUsersNotInConversation(conversationId: QualifiedIDEntity): Flow<List<UserEntity>> =
        userQueries.getUsersNotPartOfTheConversation(conversationId)
            .asFlow()
            .mapToList()
            .map { it.map(mapper::toModel) }

    override suspend fun getUsersNotInConversationByNameOrHandleOrEmail(
        conversationId: QualifiedIDEntity,
        searchQuery: String
    ): Flow<List<UserEntity>> =
        userQueries.getUsersNotInConversationByNameOrHandleOrEmail(conversationId, searchQuery)
            .asFlow()
            .mapToList()
            .map { it.map(mapper::toModel) }

    override suspend fun getUsersNotInConversationByHandle(conversationId: QualifiedIDEntity, handle: String): Flow<List<UserEntity>> =
        userQueries.getUsersNotInConversationByHandle(conversationId, handle)
            .asFlow()
            .mapToList()
            .map { it.map(mapper::toModel) }

    override suspend fun insertOrIgnoreUserWithConnectionStatus(qualifiedID: QualifiedIDEntity, connectionStatus: ConnectionEntity.State) {
        userQueries.insertOrIgnoreUserIdWithConnectionStatus(qualifiedID, connectionStatus)
    }

    override fun observeAllUsersByConnectionStatus(connectionState: ConnectionEntity.State): Flow<List<UserEntity>> =
        userQueries.selectAllUsersWithConnectionStatus(connectionState)
            .asFlow()
            .mapToList()
            .map { it.map(mapper::toModel) }

    override suspend fun getAllUsersByTeam(teamId: String): List<UserEntity> =
        userQueries.selectUsersByTeam(teamId)
            .executeAsList()
            .map(mapper::toModel)
}
