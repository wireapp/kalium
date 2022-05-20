package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO

internal typealias PersistenceConnectionState = com.wire.kalium.persistence.dao.UserEntity.ConnectionState

interface ConnectionStatusMapper {
    fun fromApiToDao(state: ConnectionStateDTO): PersistenceConnectionState
    fun fromDaoToApi(state: PersistenceConnectionState): ConnectionStateDTO?
    fun fromApiModel(state: ConnectionStateDTO): ConnectionState
    fun toApiModel(state: ConnectionState): ConnectionStateDTO?

}

internal class ConnectionStatusMapperImpl : ConnectionStatusMapper {
    override fun fromApiToDao(state: ConnectionStateDTO): PersistenceConnectionState = when (state) {
        ConnectionStateDTO.PENDING -> PersistenceConnectionState.PENDING
        ConnectionStateDTO.SENT -> PersistenceConnectionState.SENT
        ConnectionStateDTO.BLOCKED -> PersistenceConnectionState.BLOCKED
        ConnectionStateDTO.IGNORED -> PersistenceConnectionState.IGNORED
        ConnectionStateDTO.CANCELLED -> PersistenceConnectionState.CANCELLED
        ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT -> PersistenceConnectionState.MISSING_LEGALHOLD_CONSENT
        ConnectionStateDTO.ACCEPTED -> PersistenceConnectionState.ACCEPTED
    }

    override fun fromDaoToApi(state: PersistenceConnectionState): ConnectionStateDTO? = when (state) {
        PersistenceConnectionState.PENDING -> ConnectionStateDTO.PENDING
        PersistenceConnectionState.SENT -> ConnectionStateDTO.SENT
        PersistenceConnectionState.BLOCKED -> ConnectionStateDTO.BLOCKED
        PersistenceConnectionState.IGNORED -> ConnectionStateDTO.IGNORED
        PersistenceConnectionState.CANCELLED -> ConnectionStateDTO.CANCELLED
        PersistenceConnectionState.MISSING_LEGALHOLD_CONSENT -> ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT
        PersistenceConnectionState.ACCEPTED -> ConnectionStateDTO.ACCEPTED
        PersistenceConnectionState.NOT_CONNECTED -> null
    }

    override fun fromApiModel(state: ConnectionStateDTO): ConnectionState = when (state) {
        ConnectionStateDTO.PENDING -> ConnectionState.PENDING
        ConnectionStateDTO.SENT -> ConnectionState.SENT
        ConnectionStateDTO.BLOCKED -> ConnectionState.BLOCKED
        ConnectionStateDTO.IGNORED -> ConnectionState.IGNORED
        ConnectionStateDTO.CANCELLED -> ConnectionState.CANCELLED
        ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT -> ConnectionState.MISSING_LEGALHOLD_CONSENT
        ConnectionStateDTO.ACCEPTED -> ConnectionState.ACCEPTED
    }

    override fun toApiModel(state: ConnectionState): ConnectionStateDTO? = when (state) {
        ConnectionState.PENDING -> ConnectionStateDTO.PENDING
        ConnectionState.SENT -> ConnectionStateDTO.SENT
        ConnectionState.BLOCKED -> ConnectionStateDTO.BLOCKED
        ConnectionState.IGNORED -> ConnectionStateDTO.IGNORED
        ConnectionState.CANCELLED -> ConnectionStateDTO.CANCELLED
        ConnectionState.MISSING_LEGALHOLD_CONSENT -> ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT
        ConnectionState.ACCEPTED -> ConnectionStateDTO.ACCEPTED
        ConnectionState.NOT_CONNECTED -> null
    }
}

