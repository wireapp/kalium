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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

internal interface ConnectionRepositoryArrangement {
    val connectionRepository: ConnectionRepository

    suspend fun withGetConnections(result: Either<StorageFailure, Flow<List<ConversationDetails>>>)
    suspend fun withDeleteConnection(result: Either<StorageFailure, Unit>, connection: Matcher<Connection> = AnyMatcher(valueOf()))
    suspend fun withConnectionList(connectionsFlow: Flow<List<ConversationDetails>>)
    suspend fun withUpdateConnectionStatus(result: Either<CoreFailure, Connection>)
    suspend fun withIgnoreConnectionRequest(result: Either<CoreFailure, Unit>)
}

internal open class ConnectionRepositoryArrangementImpl : ConnectionRepositoryArrangement {

    override val connectionRepository: ConnectionRepository = mock(ConnectionRepository::class)

    override suspend fun withGetConnections(
        result: Either<StorageFailure, Flow<List<ConversationDetails>>>,
    ) {
        coEvery {
            connectionRepository.getConnections()
        }.returns(result)
    }

    override suspend fun withDeleteConnection(
        result: Either<StorageFailure, Unit>,
        connection: Matcher<Connection>,
    ) {
        coEvery {
            connectionRepository.deleteConnection(matches { connection.matches(it) })
        }.returns(result)
    }

    override suspend fun withConnectionList(connectionsFlow: Flow<List<ConversationDetails>>) {
        coEvery {
            connectionRepository.observeConnectionRequestsForNotification()
        }.returns(connectionsFlow)
    }

    override suspend fun withUpdateConnectionStatus(result: Either<CoreFailure, Connection>) {
        coEvery {
            connectionRepository.updateConnectionStatus(any(), any())
        }.returns(result)
    }

    override suspend fun withIgnoreConnectionRequest(result: Either<CoreFailure, Unit>) {
        coEvery {
            connectionRepository.ignoreConnectionRequest(any())
        }.returns(result)
    }
}
