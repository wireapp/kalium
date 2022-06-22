package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
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
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ConnectionRepository {
    suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit>
    suspend fun sendUserConnection(userId: UserId): Either<CoreFailure, Unit>
    suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Connection>
    suspend fun getConnections(): Either<StorageFailure, Flow<List<Connection>>>
    suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit>
    suspend fun observeConnectionList(): Flow<List<Connection>>
}

internal class ConnectionDataSource(
    private val conversationDAO: ConversationDAO,
    private val connectionDAO: ConnectionDAO,
    private val connectionApi: ConnectionApi,
    private val userDetailsApi: UserDetailsApi,
    private val userDAO: UserDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val connectionStatusMapper: ConnectionStatusMapper = MapperProvider.connectionStatusMapper(),
    private val connectionMapper: ConnectionMapper = MapperProvider.connectionMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
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
                it.connections.forEach { connectionDTO ->
                    persistConnection(connectionMapper.fromApiToModel(connectionDTO))
                }
                updateUserConnectionStatus(connections = it.connections)
                lastPagingState = it.pagingState
                hasMore = it.hasMore
            }.onFailure {
                Either.Left(it)
            }.map { }
        }

        return latestResult
    }

    override suspend fun sendUserConnection(userId: UserId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            connectionApi.createConnection(idMapper.toApiModel(userId))
        }.flatMap { connection ->
            val connectionSent = connection.copy(status = ConnectionStateDTO.SENT)
            updateUserConnectionStatus(listOf(connectionSent))
            persistConnection(connectionMapper.fromApiToModel(connection))
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
            updateUserConnectionStatus(listOf(connectionStatus))
            persistConnection(connectionModel)
            connectionModel
        }
    }

    /**
     * Check if we can transition to the correct connection status
     * [ConnectionState.CANCELLED] [ConnectionState.IGNORED] or [ConnectionState.ACCEPTED]
     */
    private fun isValidConnectionState(connectionState: ConnectionState): Boolean = when (connectionState) {
        ConnectionState.IGNORED, ConnectionState.CANCELLED, ConnectionState.ACCEPTED -> true
        else -> false
    }

    override suspend fun getConnections(): Either<StorageFailure, Flow<List<Connection>>> = wrapStorageRequest {
        observeConnectionList()
    }

    override suspend fun observeConnectionList(): Flow<List<Connection>> {
        return connectionDAO.getConnectionRequests().map {
            it.map { connection ->
                val otherUser = userDAO.getUserByQualifiedID(connection.qualifiedToId)
                connectionMapper.fromDaoToModel(connection, otherUser.first())
            }
        }
    }

    override suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit> =
        when (event.connection.status) {
            ConnectionState.CANCELLED -> deleteCancelledConnection(idMapper.toDaoModel(event.connection.qualifiedConversationId))
            ConnectionState.NOT_CONNECTED,
            ConnectionState.PENDING,
            ConnectionState.SENT,
            ConnectionState.BLOCKED,
            ConnectionState.IGNORED,
            ConnectionState.MISSING_LEGALHOLD_CONSENT,
            ConnectionState.ACCEPTED -> persistConnection(event.connection)
        }

    private suspend fun persistConnection(
        connection: Connection,
    ) = wrapStorageRequest {
        connectionDAO.insertConnection(connectionMapper.modelToDao(connection))
    }.flatMap {
        // This can fail, but the connection will be there and get synced in worst case scenario in next SlowSync
        wrapApiRequest {
            userDetailsApi.getUserInfo(idMapper.toApiModel(connection.qualifiedToId))
        }.flatMap {
            wrapStorageRequest {
                val userEntity = publicUserMapper.fromUserApiToEntity(it, connectionStatusMapper.toDaoModel(connection.status))
                userDAO.insertUser(userEntity)
                connectionDAO.updateConnectionLastUpdatedTime(connection.lastUpdate, connection.toId)
            }
        }
    }

    private suspend fun updateUserConnectionStatus(connections: List<ConnectionDTO>) {
        connections.forEach { connection ->
            when (connection.status) {
                ConnectionStateDTO.PENDING -> updateConversationMemberFromConnection(connection)
                ConnectionStateDTO.SENT -> updateConversationMemberFromConnection(connection)
                ConnectionStateDTO.BLOCKED -> updateConversationMemberFromConnection(connection)
                ConnectionStateDTO.IGNORED -> updateConversationMemberFromConnection(connection)
                ConnectionStateDTO.CANCELLED -> {
                    kaliumLogger.d("cleaning cancelled request")
                    deleteCancelledConnection(idMapper.fromApiToDao(connection.qualifiedConversationId))
                }
                ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT -> updateConversationMemberFromConnection(connection)
                ConnectionStateDTO.ACCEPTED -> updateConversationMemberFromConnection(connection)
            }
        }
    }

    private suspend fun deleteCancelledConnection(conversationId: ConversationIDEntity) = wrapStorageRequest {
        conversationDAO.deleteConversationByQualifiedID(conversationId)
    }

    private suspend fun updateConversationMemberFromConnection(connectionDTO: ConnectionDTO) {
        wrapStorageRequest {
            conversationDAO.updateOrInsertOneOnOneMemberWithConnectionStatus(
                userId = idMapper.fromApiToDao(connectionDTO.qualifiedToId),
                status = connectionStatusMapper.fromApiToDao(state = connectionDTO.status),
                conversationID = idMapper.fromApiToDao(connectionDTO.qualifiedConversationId)
            )
        }.onFailure {
            kaliumLogger.e("There was an error when trying to persist the connection: $connectionDTO")
        }
    }
}
