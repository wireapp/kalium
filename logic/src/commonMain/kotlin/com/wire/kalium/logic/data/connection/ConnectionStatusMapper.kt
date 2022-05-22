package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import com.wire.kalium.persistence.dao.UserEntity

interface ConnectionStatusMapper {
    fun fromApiToDao(state: ConnectionStateDTO): UserEntity.ConnectionState
    fun fromDaoToApi(state: UserEntity.ConnectionState): ConnectionStateDTO?
    fun fromApiModel(state: ConnectionStateDTO): ConnectionState
    fun toApiModel(state: ConnectionState): ConnectionStateDTO?

}

internal class ConnectionStatusMapperImpl : ConnectionStatusMapper {
    override fun fromApiToDao(state: ConnectionStateDTO): UserEntity.ConnectionState = when (state) {
        ConnectionStateDTO.PENDING -> UserEntity.ConnectionState.PENDING
        ConnectionStateDTO.SENT -> UserEntity.ConnectionState.SENT
        ConnectionStateDTO.BLOCKED -> UserEntity.ConnectionState.BLOCKED
        ConnectionStateDTO.IGNORED -> UserEntity.ConnectionState.IGNORED
        ConnectionStateDTO.CANCELLED -> UserEntity.ConnectionState.CANCELLED
        ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT -> UserEntity.ConnectionState.MISSING_LEGALHOLD_CONSENT
        ConnectionStateDTO.ACCEPTED -> UserEntity.ConnectionState.ACCEPTED
    }

    override fun fromDaoToApi(state: UserEntity.ConnectionState): ConnectionStateDTO? = when (state) {
        UserEntity.ConnectionState.PENDING -> ConnectionStateDTO.PENDING
        UserEntity.ConnectionState.SENT -> ConnectionStateDTO.SENT
        UserEntity.ConnectionState.BLOCKED -> ConnectionStateDTO.BLOCKED
        UserEntity.ConnectionState.IGNORED -> ConnectionStateDTO.IGNORED
        UserEntity.ConnectionState.CANCELLED -> ConnectionStateDTO.CANCELLED
        UserEntity.ConnectionState.MISSING_LEGALHOLD_CONSENT -> ConnectionStateDTO.MISSING_LEGALHOLD_CONSENT
        UserEntity.ConnectionState.ACCEPTED -> ConnectionStateDTO.ACCEPTED
        UserEntity.ConnectionState.NOT_CONNECTED -> null
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

