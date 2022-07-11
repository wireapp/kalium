package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionState.ACCEPTED
import com.wire.kalium.logic.data.user.ConnectionState.BLOCKED
import com.wire.kalium.logic.data.user.ConnectionState.CANCELLED
import com.wire.kalium.logic.data.user.ConnectionState.IGNORED
import com.wire.kalium.logic.data.user.ConnectionState.MISSING_LEGALHOLD_CONSENT
import com.wire.kalium.logic.data.user.ConnectionState.NOT_CONNECTED
import com.wire.kalium.logic.data.user.ConnectionState.PENDING
import com.wire.kalium.logic.data.user.ConnectionState.SENT
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.InvalidMappingFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionDTO
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface ConnectionRepository {
    suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit>
    suspend fun sendUserConnection(userId: UserId): Either<CoreFailure, Unit>
    suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Connection>
    suspend fun getConnections(): Either<StorageFailure, Flow<List<ConversationDetails>>>
    suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit>
    suspend fun observeConnectionList(): Flow<List<ConversationDetails>>
    suspend fun observeConnectionListAsDetails(): Flow<List<ConversationDetails>>
    suspend fun getConnectionRequests(): List<Connection>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConnectionDataSource(
    private val conversationDAO: ConversationDAO,
    private val connectionDAO: ConnectionDAO,
    private val connectionApi: ConnectionApi,
    private val userDetailsApi: UserDetailsApi,
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val connectionStatusMapper: ConnectionStatusMapper = MapperProvider.connectionStatusMapper(),
    private val connectionMapper: ConnectionMapper = MapperProvider.connectionMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
    private val userTypeEntityTypeMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper()
) : ConnectionRepository {

    override suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit> {
        var hasMore = true
        var lastPagingState: String? = null
        var latestResult: Either<NetworkFailure, Unit> = Either.Right(Unit)

        while (hasMore && latestResult.isRight()) {
            latestResult = wrapApiRequest {
                kaliumLogger.v("Fetching connections page starting with pagingState $lastPagingState")
                connectionApi.fetchSelfUserConnections(pagingState = lastPagingState)
            }.onSuccess {
                syncConnectionsStatuses(it.connections)
                lastPagingState = it.pagingState
                hasMore = it.hasMore
            }.onFailure {
                Either.Left(it)
            }.map { }
        }

        return latestResult
    }

    private suspend fun syncConnectionsStatuses(connections: List<ConnectionDTO>) {
        connections.forEach { connectionDTO ->
            handleUserConnectionStatusPersistence(connectionMapper.fromApiToModel(connectionDTO))
        }
    }

    override suspend fun sendUserConnection(userId: UserId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            connectionApi.createConnection(idMapper.toApiModel(userId))
        }.flatMap { connection ->
            val connectionSent = connection.copy(status = ConnectionStateDTO.SENT)
            handleUserConnectionStatusPersistence(connectionMapper.fromApiToModel(connectionSent))
        }.map { }
    }

    override suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Connection> {
        val isValidConnectionState = isValidConnectionState(connectionState)
        val newConnectionStatus = connectionStatusMapper.toApiModel(connectionState)
        if (!isValidConnectionState || newConnectionStatus == null) {
            return Either.Left(InvalidMappingFailure)
        }

        return wrapApiRequest {
            connectionApi.updateConnection(idMapper.toApiModel(userId), newConnectionStatus)
        }.map { connectionDTO ->
            val connectionStatus = connectionDTO.copy(status = newConnectionStatus)
            val connectionModel = connectionMapper.fromApiToModel(connectionDTO)
            handleUserConnectionStatusPersistence(connectionMapper.fromApiToModel(connectionStatus))
            persistConnection(connectionModel)
            connectionModel
        }
    }

    /**
     * Check if we can transition to the correct connection status
     * [ConnectionState.CANCELLED] [ConnectionState.IGNORED] or [ConnectionState.ACCEPTED]
     */
    private fun isValidConnectionState(connectionState: ConnectionState): Boolean = when (connectionState) {
        IGNORED, CANCELLED, ACCEPTED -> true
        else -> false
    }

    override suspend fun getConnections(): Either<StorageFailure, Flow<List<ConversationDetails>>> = wrapStorageRequest {
        observeConnectionList()
    }

    override suspend fun observeConnectionList(): Flow<List<ConversationDetails>> {
        return connectionDAO.getConnectionRequests().map {
            it.map { connection ->
                val otherUser = userDAO.getUserByQualifiedID(connection.qualifiedToId)
                connectionMapper.fromDaoToConnectionDetails(connection, otherUser.firstOrNull())
            }
        }
    }


    override suspend fun observeConnectionListAsDetails(): Flow<List<ConversationDetails>> {
        return connectionDAO.getConnectionRequests().map {
            it.map { connection ->
                val otherUser = userDAO.getUserByQualifiedID(connection.qualifiedToId)
                connectionMapper.fromDaoToConnectionDetails(connection, otherUser.firstOrNull())
            }
        }
    }

    override suspend fun getConnectionRequests(): List<Connection> {
        return connectionDAO.getConnectionRequests().first().map { connection ->
            val otherUser = userDAO.getUserByQualifiedID(connection.qualifiedToId)
            connectionMapper.fromDaoToModel(connection, otherUser.firstOrNull())
        }
    }

    override suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit> =
        persistConnection(event.connection)

    //TODO: Vitor : Instead of duplicating, we could pass selfUser.teamId from the UseCases to this function.
    // This way, the UseCases can tie the different Repos together, calling these functions.
    private suspend fun persistConnection(
        connection: Connection,
    ) = wrapStorageRequest {
        connectionDAO.insertConnection(connectionMapper.modelToDao(connection))
    }.flatMap {
        // This can fail, but the connection will be there and get synced in worst case scenario in next SlowSync
        wrapApiRequest {
            userDetailsApi.getUserInfo(idMapper.toApiModel(connection.qualifiedToId))
        }.flatMap { userProfileDTO ->
            wrapStorageRequest {
                val selfUser = getSelfUser();
                val userEntity = publicUserMapper.fromUserApiToEntityWithConnectionStateAndUserTypeEntity(
                    userDetailResponse = userProfileDTO,
                    connectionState = connectionStatusMapper.toDaoModel(state = connection.status),
                    userTypeEntity = userTypeEntityTypeMapper.fromOtherUserTeamAndDomain(
                        otherUserDomain = userProfileDTO.id.domain,
                        selfUserTeamId = selfUser.teamId,
                        otherUserTeamId = userProfileDTO.teamId,
                        selfUserDomain = selfUser.id.domain
                    )
                )

                userDAO.insertUser(userEntity)
                connectionDAO.updateConnectionLastUpdatedTime(connection.lastUpdate, connection.toId)
            }
        }
    }

    private suspend fun deleteCancelledConnection(conversationId: ConversationId) = wrapStorageRequest {
        connectionDAO.deleteConnectionDataAndConversation(idMapper.toDaoModel(conversationId))
    }

    //TODO: code duplication here for getting self user, the same is done inside
    // UserRepository, what would be best ?
    // creating SelfUserDao managing the UserEntity corresponding to SelfUser ?
    private suspend fun getSelfUser(): SelfUser {
        return metadataDAO.valueByKey(UserDataSource.SELF_USER_ID_KEY)
            .filterNotNull()
            .flatMapMerge { encodedValue ->
                val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)

                userDAO.getUserByQualifiedID(selfUserID)
                    .filterNotNull()
                    .map(userMapper::fromDaoModelToSelfUser)
            }.firstOrNull() ?: throw IllegalStateException()
    }

    private suspend fun updateConversationMemberFromConnection(connection: Connection) =
        wrapStorageRequest {
            conversationDAO.updateOrInsertOneOnOneMemberWithConnectionStatus(
                // TODO(IMPORTANT!!!!!!): setting a default value for member role is incorrect and can lead to unexpected behaviour
                member = Member(user = idMapper.toDaoModel(connection.qualifiedToId), Member.Role.Member),
                status = connectionStatusMapper.toDaoModel(connection.status),
                conversationID = idMapper.toDaoModel(connection.qualifiedConversationId)
            )
        }.onFailure {
            kaliumLogger.e("There was an error when trying to persist the connection: $connection")
        }

    /**
     * This will update the connection status on user table and will insert members only
     * if the [ConnectionDTO.status] is other than [ConnectionStateDTO.PENDING] or [ConnectionStateDTO.SENT]
     */
    private suspend fun handleUserConnectionStatusPersistence(connection: Connection): Either<CoreFailure, Unit> =
        when (connection.status) {
            MISSING_LEGALHOLD_CONSENT, NOT_CONNECTED, PENDING, SENT, BLOCKED, IGNORED -> persistConnection(connection)
            CANCELLED -> deleteCancelledConnection(connection.qualifiedConversationId)
            ACCEPTED -> updateConversationMemberFromConnection(connection)
        }
}
