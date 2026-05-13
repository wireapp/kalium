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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.common.functional.Either
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow

internal interface ConnectionRepositoryArrangement {
    val connectionRepository: ConnectionRepository

    suspend fun withGetConnections(result: Either<StorageFailure, Flow<List<ConversationDetails>>>)
    suspend fun withDeleteConnection(result: Either<StorageFailure, Unit>, connection: (Connection) -> Boolean = { true })
    suspend fun withConnectionList(connectionsFlow: Flow<List<ConversationDetails>>)
    suspend fun withUpdateConnectionStatus(result: Either<CoreFailure, Connection>)
    suspend fun withIgnoreConnectionRequest(result: Either<CoreFailure, Unit>)
}

internal open class ConnectionRepositoryArrangementImpl : ConnectionRepositoryArrangement {

    override val connectionRepository: ConnectionRepository = mock<ConnectionRepository>(mode = MockMode.autoUnit)

    override suspend fun withGetConnections(
        result: Either<StorageFailure, Flow<List<ConversationDetails>>>,
    ) {
        everySuspend {
            connectionRepository.getConnections()
        }.returns(result)
    }

    override suspend fun withDeleteConnection(
        result: Either<StorageFailure, Unit>,
        connection: (Connection) -> Boolean,
    ) {
        everySuspend {
            connectionRepository.deleteConnection(matches { connection(it) })
        }.returns(result)
    }

    override suspend fun withConnectionList(connectionsFlow: Flow<List<ConversationDetails>>) {
        everySuspend {
            connectionRepository.observeConnectionRequestsForNotification()
        }.returns(connectionsFlow)
    }

    override suspend fun withUpdateConnectionStatus(result: Either<CoreFailure, Connection>) {
        everySuspend {
            connectionRepository.updateConnectionStatus(any(), any(), any())
        }.returns(result)
    }

    override suspend fun withIgnoreConnectionRequest(result: Either<CoreFailure, Unit>) {
        everySuspend {
            connectionRepository.ignoreConnectionRequest(any(), any())
        }.returns(result)
    }
}
