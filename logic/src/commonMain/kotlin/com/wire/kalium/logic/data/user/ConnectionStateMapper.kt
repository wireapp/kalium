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

package com.wire.kalium.logic.data.user

import com.wire.kalium.persistence.dao.ConnectionEntity
import io.mockative.Mockable

@Mockable
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
