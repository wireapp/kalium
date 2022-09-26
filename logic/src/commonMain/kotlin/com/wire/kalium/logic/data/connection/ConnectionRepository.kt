package com.wire.kalium.logic.data.connection

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONNECTIONS
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.InvalidMappingFailure
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConnectionRepository {
    suspend fun fetchSelfUserConnections(): Either<CoreFailure, Unit>
    suspend fun sendUserConnection(userId: UserId): Either<CoreFailure, Unit>
    suspend fun updateConnectionStatus(userId: UserId, connectionState: ConnectionState): Either<CoreFailure, Connection>
    suspend fun getConnections(): Either<StorageFailure, Flow<List<ConversationDetails>>>
    suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit>
    suspend fun observeConnectionList(): Flow<List<Connection>>
    suspend fun observeConnectionRequestList(): Flow<List<ConversationDetails>>
    suspend fun observeConnectionRequestsForNotification(): Flow<List<ConversationDetails>>
    suspend fun setConnectionAsNotified(userId: UserId)
    suspend fun setAllConnectionsAsNotified()
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConnectionDataSource(
    private val conversationDAO: ConversationDAO,
    private val connectionDAO: ConnectionDAO,
    private val connectionApi: ConnectionApi,
    private val userDetailsApi: UserDetailsApi,
    private val userDAO: UserDAO,
    private val selfUserId: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val conversationRepository: ConversationRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
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
     * [ConnectionState.CANCELLED] [ConnectionState.IGNORED] [ConnectionState.BLOCKED] or [ConnectionState.ACCEPTED]
     */
    private fun isValidConnectionState(connectionState: ConnectionState): Boolean = when (connectionState) {
        BLOCKED, IGNORED, CANCELLED, ACCEPTED -> true
        else -> false
    }

    override suspend fun getConnections(): Either<StorageFailure, Flow<List<ConversationDetails>>> = wrapStorageRequest {
        observeConnectionRequestList()
    }

    override suspend fun observeConnectionRequestList(): Flow<List<ConversationDetails>> {
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
        connectionDAO.updateNotificationFlag(false, idMapper.toDaoModel(userId))
    }

    override suspend fun setAllConnectionsAsNotified() {
        connectionDAO.updateAllNotificationFlags(false)
    }

    override suspend fun insertConnectionFromEvent(event: Event.User.NewConnection): Either<CoreFailure, Unit> =
        persistConnection(event.connection)

    override suspend fun observeConnectionList(): Flow<List<Connection>> {
        return connectionDAO.getConnections().map { connections ->
            connections.map { connection ->
                connectionMapper.fromDaoToModel(connection)
            }
        }
    }

    // TODO: Vitor : Instead of duplicating, we could pass selfUser.teamId from the UseCases to this function.
    // This way, the UseCases can tie the different Repos together, calling these functions.
    private suspend fun persistConnection(connection: Connection) =
        selfTeamIdProvider().flatMap { teamId ->
            // This can fail, but the connection will be there and get synced in worst case scenario in next SlowSync
            wrapApiRequest {
                userDetailsApi.getUserInfo(idMapper.toApiModel(connection.qualifiedToId))
            }.fold({
                wrapStorageRequest {
                    connectionDAO.insertConnection(connectionMapper.modelToDao(connection))
                }
            }, { userProfileDTO ->
                wrapStorageRequest {
                    val userEntity = publicUserMapper.fromUserApiToEntityWithConnectionStateAndUserTypeEntity(
                        userDetailResponse = userProfileDTO,
                        connectionState = connectionStatusMapper.toDaoModel(state = connection.status),
                        userTypeEntity = userTypeEntityTypeMapper.fromTeamAndDomain(
                            otherUserDomain = userProfileDTO.id.domain,
                            selfUserTeamId = teamId?.value,
                            otherUserTeamId = userProfileDTO.teamId,
                            selfUserDomain = selfUserId.domain,
                            isService = userProfileDTO.service != null
                        )
                    )
                    insertConversationFromConnection(connection)
                    userDAO.insertUser(userEntity)
                    connectionDAO.insertConnection(connectionMapper.modelToDao(connection))
                }
            })
        }

    private suspend fun insertConversationFromConnection(connection: Connection) {
        when (connection.status) {
            SENT -> conversationRepository.fetchConversation(connection.qualifiedConversationId)
            PENDING -> {
                /* TODO: we had to do it manually, the server won't give us for received connections
                     as the final solution we need to ignore the conversation part, but now? we can't! */
                conversationDAO.insertConversation(
                    conversationEntity = ConversationEntity(
                        id = idMapper.toDaoModel(connection.qualifiedConversationId),
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
                        isCreator = false
                    )
                )
            }

            else -> {}
        }
    }

    private suspend fun deleteCancelledConnection(conversationId: ConversationId) = wrapStorageRequest {
        connectionDAO.deleteConnectionDataAndConversation(idMapper.toDaoModel(conversationId))
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
