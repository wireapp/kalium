package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.InvalidMappingFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.connection.Connection
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionStatusDTO
import com.wire.kalium.persistence.dao.ConversationDAO

interface ConnectionRepository {
    suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit>
    suspend fun sendUserConnection(userId: UserId): Either<CoreFailure, Unit>
    suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Unit>
}

internal class ConnectionDataSource(
    private val conversationDAO: ConversationDAO,
    private val connectionApi: ConnectionApi,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val connectionStatusMapper: ConnectionStatusMapper = MapperProvider.connectionStatusMapper()
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
        }.map { connection ->
            val connectionSent = connection.copy(status = ConnectionStatusDTO.SENT)
            updateUserConnectionStatus(listOf(connectionSent))
        }
    }

    override suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Unit> {
        val connectionStatus = connectionStatusMapper.connectionStateToApi(connectionState)
        return if (connectionStatus == null)
            Either.Left(InvalidMappingFailure)
        else {
            wrapApiRequest {
                connectionApi.updateConnection(idMapper.toApiModel(userId), connectionStatus)
            }.map { connection ->
                val connectionSent = connection.copy(status = connectionStatus)
                updateUserConnectionStatus(listOf(connectionSent))
            }
        }
    }

    private suspend fun updateUserConnectionStatus(
        connections: List<Connection>
    ) {
        wrapStorageRequest {
            connections.forEach { connection ->
                conversationDAO.updateOrInsertOneOnOneMemberWithConnectionStatus(
                    userId = idMapper.fromApiToDao(connection.qualifiedToId),
                    status = connectionStatusMapper.connectionStateToDao(state = connection.status),
                    conversationID = idMapper.fromApiToDao(connection.qualifiedConversationId)
                )
            }
        }
    }
}
