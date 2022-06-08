package com.wire.kalium.logic.data.user

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity

interface ConnectionStateMapper {
    fun fromDaoConnectionStateToUser(connectionState: ConnectionEntity.State): ConnectionState
    fun fromUserConnectionStateToDao(connectionState: ConnectionState): ConnectionEntity.State
}

internal class ConnectionStateMapperImpl : ConnectionStateMapper {

    override fun fromDaoConnectionStateToUser(connectionState: ConnectionEntity.State): ConnectionState =
        when (connectionState) {
            ConnectionEntity.State.NOT_CONNECTED -> ConnectionState.NOT_CONNECTED
            ConnectionEntity.State.PENDING -> ConnectionState.PENDING
            ConnectionEntity.State.SENT -> ConnectionState.SENT
            ConnectionEntity.State.BLOCKED -> ConnectionState.BLOCKED
            ConnectionEntity.State.IGNORED -> ConnectionState.IGNORED
            ConnectionEntity.State.CANCELLED -> ConnectionState.CANCELLED
            ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT -> ConnectionState.MISSING_LEGALHOLD_CONSENT
            ConnectionEntity.State.ACCEPTED -> ConnectionState.ACCEPTED
        }

    override fun fromUserConnectionStateToDao(connectionState: ConnectionState): ConnectionEntity.State =
        when (connectionState) {
            ConnectionState.NOT_CONNECTED -> ConnectionEntity.State.NOT_CONNECTED
            ConnectionState.PENDING -> ConnectionEntity.State.PENDING
            ConnectionState.SENT -> ConnectionEntity.State.SENT
            ConnectionState.BLOCKED -> ConnectionEntity.State.BLOCKED
            ConnectionState.IGNORED -> ConnectionEntity.State.IGNORED
            ConnectionState.CANCELLED -> ConnectionEntity.State.CANCELLED
            ConnectionState.MISSING_LEGALHOLD_CONSENT -> ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT
            ConnectionState.ACCEPTED -> ConnectionEntity.State.ACCEPTED
        }
}
