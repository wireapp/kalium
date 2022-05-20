package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import com.wire.kalium.persistence.dao.UserEntity

interface ConnectionStatusMapper {
    fun connectionStateToDao(state: ConnectionStateDTO): UserEntity.ConnectionState
    fun connectionStateToApi(state: ConnectionState): ConnectionStateDTO?
}

internal class ConnectionStatusMapperImpl : ConnectionStatusMapper {
    override fun connectionStateToDao(state: ConnectionStateDTO): UserEntity.ConnectionState = when (state) {
        ConnectionStateDTO.PENDING -> UserEntity.ConnectionState.PENDING
        ConnectionStateDTO.SENT -> UserEntity.ConnectionState.SENT
        ConnectionStateDTO.BLOCKED -> UserEntity.ConnectionState.BLOCKED
        ConnectionStateDTO.IGNORED -> UserEntity.ConnectionState.IGNORED
        ConnectionStateDTO.CANCELLED -> UserEntity.ConnectionState.CANCELLED
        ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT -> UserEntity.ConnectionState.MISSING_LEGALHOLD_CONSENT
        ConnectionStateDTO.ACCEPTED -> UserEntity.ConnectionState.ACCEPTED
    }

    override fun connectionStateToApi(state: ConnectionState): ConnectionStateDTO? = when (state) {
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

