package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import com.wire.kalium.persistence.dao.ConnectionEntity

interface ConnectionStatusMapper {
    fun fromApiToDao(state: ConnectionStateDTO): ConnectionEntity.State
    fun fromDaoToApi(state: ConnectionEntity.State): ConnectionStateDTO?
    fun fromApiModel(state: ConnectionStateDTO): ConnectionState
    fun toApiModel(state: ConnectionState): ConnectionStateDTO?

}

internal class ConnectionStatusMapperImpl : ConnectionStatusMapper {
    override fun fromApiToDao(state: ConnectionStateDTO): ConnectionEntity.State = when (state) {
        ConnectionStateDTO.PENDING -> ConnectionEntity.State.PENDING
        ConnectionStateDTO.SENT -> ConnectionEntity.State.SENT
        ConnectionStateDTO.BLOCKED -> ConnectionEntity.State.BLOCKED
        ConnectionStateDTO.IGNORED -> ConnectionEntity.State.IGNORED
        ConnectionStateDTO.CANCELLED -> ConnectionEntity.State.CANCELLED
        ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT -> ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT
        ConnectionStateDTO.ACCEPTED -> ConnectionEntity.State.ACCEPTED
    }

    override fun fromDaoToApi(state: ConnectionEntity.State): ConnectionStateDTO? = when (state) {
        ConnectionEntity.State.PENDING -> ConnectionStateDTO.PENDING
        ConnectionEntity.State.SENT -> ConnectionStateDTO.SENT
        ConnectionEntity.State.BLOCKED -> ConnectionStateDTO.BLOCKED
        ConnectionEntity.State.IGNORED -> ConnectionStateDTO.IGNORED
        ConnectionEntity.State.CANCELLED -> ConnectionStateDTO.CANCELLED
        ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT -> ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT
        ConnectionEntity.State.ACCEPTED -> ConnectionStateDTO.ACCEPTED
        ConnectionEntity.State.NOT_CONNECTED -> null
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

