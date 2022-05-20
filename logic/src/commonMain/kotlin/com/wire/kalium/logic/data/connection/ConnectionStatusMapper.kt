package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.user.connection.ConnectionStatusDTO
import com.wire.kalium.persistence.dao.UserEntity

interface ConnectionStatusMapper {
    fun connectionStateToDao(state: ConnectionStatusDTO): UserEntity.ConnectionState
    fun connectionStateToApi(state: ConnectionState): ConnectionStatusDTO?
}

internal class ConnectionStatusMapperImpl : ConnectionStatusMapper {
    override fun connectionStateToDao(state: ConnectionStatusDTO): UserEntity.ConnectionState = when (state) {
        ConnectionStatusDTO.PENDING -> UserEntity.ConnectionState.PENDING
        ConnectionStatusDTO.SENT -> UserEntity.ConnectionState.SENT
        ConnectionStatusDTO.BLOCKED -> UserEntity.ConnectionState.BLOCKED
        ConnectionStatusDTO.IGNORED -> UserEntity.ConnectionState.IGNORED
        ConnectionStatusDTO.CANCELLED -> UserEntity.ConnectionState.CANCELLED
        ConnectionStatusDTO.MISSING_LEGALHOLD_CONSENT -> UserEntity.ConnectionState.MISSING_LEGALHOLD_CONSENT
        ConnectionStatusDTO.ACCEPTED -> UserEntity.ConnectionState.ACCEPTED
    }

    override fun connectionStateToApi(state: ConnectionState): ConnectionStatusDTO? = when (state) {
        ConnectionState.PENDING -> ConnectionStatusDTO.PENDING
        ConnectionState.SENT -> ConnectionStatusDTO.SENT
        ConnectionState.BLOCKED -> ConnectionStatusDTO.BLOCKED
        ConnectionState.IGNORED -> ConnectionStatusDTO.IGNORED
        ConnectionState.CANCELLED -> ConnectionStatusDTO.CANCELLED
        ConnectionState.MISSING_LEGALHOLD_CONSENT -> ConnectionStatusDTO.MISSING_LEGALHOLD_CONSENT
        ConnectionState.ACCEPTED -> ConnectionStatusDTO.ACCEPTED
        ConnectionState.NOT_CONNECTED -> null
    }
}

