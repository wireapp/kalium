/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.connection

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONNECTIONS
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.InvalidMappingFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConnectionRepository {
    suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit>
    suspend fun sendUserConnection(userId: UserId): Either<CoreFailure, Unit>
    suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Connection>
    suspend fun getConnections(): Either<StorageFailure, Flow<List<ConversationDetails>>>
    suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit>
    suspend fun observeConnectionList(): Flow<List<Connection>>
    suspend fun observeConnectionRequestList(): Flow<List<ConversationDetails.Connection>>
    suspend fun observeConnectionRequestsForNotification(): Flow<List<ConversationDetails>>
    suspend fun setConnectionAsNotified(userId: UserId)
    suspend fun setAllConnectionsAsNotified()
    suspend fun deleteConnection(connection: Connection): Either<StorageFailure, Unit>
    suspend fun getConnection(conversationId: ConversationId): Either<StorageFailure, ConversationDetails.Connection>
    suspend fun ignoreConnectionRequest(userId: UserId): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConnectionDataSource(
    private val conversationDAO: ConversationDAO,
    private val memberDAO: MemberDAO,
    private val connectionDAO: ConnectionDAO,
    private val connectionApi: ConnectionApi,
    private val userDAO: UserDAO,
    private val conversationRepository: ConversationRepository,
    private val connectionStatusMapper: ConnectionStatusMapper = MapperProvider.connectionStatusMapper(),
    private val connectionMapper: ConnectionMapper = MapperProvider.connectionMapper()
) : ConnectionRepository {

    override suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit> {
        var hasMore = true
        var lastPagingState: String? = null
        var latestResult: Either<NetworkFailure, Unit> = Either.Right(Unit)

        while (hasMore && latestResult.isRight()) {
            latestResult = wrapApiRequest {
                kaliumLogger.withFeatureId(CONNECTIONS).v("Fetching connections page starting with pagingState $lastPagingState")
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
            connectionApi.createConnection(userId.toApi())
        }.flatMap { connection ->
            handleUserConnectionStatusPersistence(connectionMapper.fromApiToModel(connection))
        }.map { }
    }

    private suspend fun updateRemoteConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, ConnectionDTO> {
        val isValidConnectionState = isValidConnectionState(connectionState)
        val newConnectionStatus = connectionStatusMapper.toApiModel(connectionState)
        if (!isValidConnectionState || newConnectionStatus == null) {
            return Either.Left(InvalidMappingFailure)
        }

        return wrapApiRequest { connectionApi.updateConnection(userId.toApi(), newConnectionStatus) }
    }

    override suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Connection> =
        updateRemoteConnectionStatus(userId, connectionState).map { connectionDTO ->
            val connectionStatus = connectionDTO.copy(status = connectionStatusMapper.toApiModel(connectionState)!!)
            val connectionModel = connectionMapper.fromApiToModel(connectionDTO)
            handleUserConnectionStatusPersistence(connectionMapper.fromApiToModel(connectionStatus))
            connectionModel
        }

    override suspend fun ignoreConnectionRequest(userId: UserId): Either<CoreFailure, Unit> =
        updateRemoteConnectionStatus(userId, IGNORED)
            .flatMapLeft {
                if (it is NetworkFailure.FederatedBackendFailure.FailedDomains) Either.Right(Unit) else it.left()
            }
            .onSuccess {
                setConnectionStatus(userId, IGNORED)
            }.map { Unit }

    private suspend fun setConnectionStatus(userId: UserId, status: ConnectionState): Either<CoreFailure, Unit> =
        wrapStorageRequest { connectionDAO.getConnectionByUser(userId.toDao()) }
            .map { connectionEntity ->
                val updatedConnection = connectionMapper.fromDaoToModel(connectionEntity).copy(status = status)
                handleUserConnectionStatusPersistence(updatedConnection)
            }

    /**
     * Check if we can transition to the correct connection status
     * [ConnectionState.CANCELLED] [ConnectionState.IGNORED] [ConnectionState.BLOCKED] or [ConnectionState.ACCEPTED]
     */
    private fun isValidConnectionState(connectionState: ConnectionState): Boolean = when (connectionState) {
        BLOCKED, IGNORED, CANCELLED, ACCEPTED -> true
        else -> false
    }

    override suspend fun getConnections(): Either<StorageFailure, Flow<List<ConversationDetails>>> = wrapStorageRequest {
        observeConnectionRequestList()
    }

    override suspend fun observeConnectionRequestList(): Flow<List<ConversationDetails.Connection>> {
        return connectionDAO.getConnectionRequests().map { connections ->
            connections
                .map { connection ->
                    connectionMapper.fromDaoToConversationDetails(connection)
                }
        }
    }

    override suspend fun observeConnectionRequestsForNotification(): Flow<List<ConversationDetails>> {
        return connectionDAO.getConnectionRequestsForNotification()
            .map {
                it.map { connection ->
                    connectionMapper.fromDaoToConversationDetails(connection)
                }
            }
    }

    override suspend fun setConnectionAsNotified(userId: UserId) {
        connectionDAO.updateNotificationFlag(false, userId.toDao())
    }

    override suspend fun setAllConnectionsAsNotified() {
        connectionDAO.setAllConnectionsAsNotified()
    }

    override suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit> =
        handleUserConnectionStatusPersistence(event.connection)

    override suspend fun observeConnectionList(): Flow<List<Connection>> {
        return connectionDAO.getConnections().map { connections ->
            connections.map { connection ->
                connectionMapper.fromDaoToModel(connection)
            }
        }
    }

    private suspend fun persistConnection(connection: Connection) =
        wrapStorageRequest {
            val connectionStatus = connectionStatusMapper.toDaoModel(state = connection.status)
            userDAO.upsertConnectionStatuses(mapOf(connection.qualifiedToId.toDao() to connectionStatus))

            insertConversationFromConnection(connection)

            if (connection.status != ACCEPTED) {
                connectionDAO.insertConnection(connectionMapper.modelToDao(connection))
            }
        }

    private suspend fun insertConversationFromConnection(connection: Connection) {
        when (connection.status) {
            SENT -> conversationRepository.fetchSentConnectionConversation(connection.qualifiedConversationId)
            PENDING -> {
                /* TODO: we had to do it manually, the server won't give us for received connections
                     as the final solution we need to ignore the conversation part, but now? we can't! */
                conversationDAO.insertConversation(
                    conversationEntity = ConversationEntity(
                        id = connection.qualifiedConversationId.toDao(),
                        name = null,
                        type = ConversationEntity.Type.CONNECTION_PENDING,
                        teamId = null,
                        protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                        creatorId = connection.from,
                        lastNotificationDate = null,
                        lastModifiedDate = connection.lastUpdate,
                        lastReadDate = connection.lastUpdate,
                        access = emptyList(),
                        accessRole = emptyList(),
                        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
                        messageTimer = null,
                        userMessageTimer = null,
                        archived = false,
                        archivedInstant = null,
                        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED
                    )
                )
            }

            ACCEPTED -> {
                memberDAO.updateOrInsertOneOnOneMember(
                    member = MemberEntity(user = connection.qualifiedToId.toDao(), MemberEntity.Role.Member),
                    conversationID = connection.qualifiedConversationId.toDao()
                )
            }

            NOT_CONNECTED, BLOCKED, IGNORED, CANCELLED,
            MISSING_LEGALHOLD_CONSENT -> {
                kaliumLogger.i("INSERT CONVERSATION FROM CONNECTION NOT ENGAGED FOR $connection")
            }
        }
    }

    override suspend fun deleteConnection(connection: Connection) = wrapStorageRequest {
        connectionDAO.deleteConnectionDataAndConversation(connection.qualifiedConversationId.toDao())
        userDAO.upsertConnectionStatuses(mapOf(connection.qualifiedToId.toDao() to ConnectionEntity.State.CANCELLED))
    }

    override suspend fun getConnection(conversationId: ConversationId) = wrapStorageRequest {
            connectionDAO.getConnection(conversationId.toDao())?.let { connectionMapper.fromDaoToConversationDetails(it) }
    }

    /**
     * This will update the connection status on user table and will insert members only
     * if the [ConnectionDTO.status] is other than [ConnectionStateDTO.PENDING] or [ConnectionStateDTO.SENT]
     */
    private suspend fun handleUserConnectionStatusPersistence(connection: Connection): Either<CoreFailure, Unit> =
        when (connection.status) {
            ACCEPTED, MISSING_LEGALHOLD_CONSENT, NOT_CONNECTED, PENDING, SENT, BLOCKED, IGNORED -> persistConnection(connection)
            CANCELLED -> deleteConnection(connection)
        }
}
