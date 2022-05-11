package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
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
import com.wire.kalium.network.api.user.connection.ConnectionState
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserEntity

interface ConnectionRepository {
    suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit>
}

internal class ConnectionDataSource(
    private val conversationDAO: ConversationDAO,
    private val connectionApi: ConnectionApi,
    private val idMapper: IdMapper = MapperProvider.idMapper()
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

    private fun connectionStateToDao(state: ConnectionState): UserEntity.ConnectionState = when (state) {
        ConnectionState.PENDING -> UserEntity.ConnectionState.PENDING
        ConnectionState.SENT -> UserEntity.ConnectionState.SENT
        ConnectionState.BLOCKED -> UserEntity.ConnectionState.BLOCKED
        ConnectionState.IGNORED -> UserEntity.ConnectionState.IGNORED
        ConnectionState.CANCELLED -> UserEntity.ConnectionState.CANCELLED
        ConnectionState.MISSING_LEGALHOLD_CONSENT -> UserEntity.ConnectionState.MISSING_LEGALHOLD_CONSENT
        ConnectionState.ACCEPTED -> UserEntity.ConnectionState.ACCEPTED
    }

    private suspend fun updateUserConnectionStatus(
        connections: List<Connection>
    ) {
        wrapStorageRequest {
            connections.forEach { connection ->
                conversationDAO.insertOrUpdateOneOnOneMemberWithConnectionStatus(
                    userId = idMapper.fromApiToDao(connection.qualifiedToId),
                    status = connectionStateToDao(state = connection.status),
                    conversationID = idMapper.fromApiToDao(connection.qualifiedConversationId)
                )
            }
        }
    }
}
