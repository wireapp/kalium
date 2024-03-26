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
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

internal interface ConnectionRepositoryArrangement {
    val connectionRepository: ConnectionRepository

    fun withGetConnections(result: Either<StorageFailure, Flow<List<ConversationDetails>>>)
    fun withDeleteConnection(result: Either<StorageFailure, Unit>, connection: Matcher<Connection> = any())
    fun withConnectionList(connectionsFlow: Flow<List<ConversationDetails>>)
    fun withUpdateConnectionStatus(result: Either<CoreFailure, Connection>)
}

internal open class ConnectionRepositoryArrangementImpl : ConnectionRepositoryArrangement {
    @Mock
    override val connectionRepository: ConnectionRepository = mock(ConnectionRepository::class)

    override fun withGetConnections(
        result: Either<StorageFailure, Flow<List<ConversationDetails>>>,
    ) {
        given(connectionRepository)
            .suspendFunction(connectionRepository::getConnections)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withDeleteConnection(
        result: Either<StorageFailure, Unit>,
        connection: Matcher<Connection>,
    ) {
        given(connectionRepository)
            .suspendFunction(connectionRepository::deleteConnection)
            .whenInvokedWith(connection)
            .thenReturn(result)
    }

    override fun withConnectionList(connectionsFlow: Flow<List<ConversationDetails>>) {
        given(connectionRepository)
            .suspendFunction(connectionRepository::observeConnectionRequestsForNotification)
            .whenInvoked()
            .thenReturn(connectionsFlow)
    }

    override fun withUpdateConnectionStatus(result: Either<CoreFailure, Connection>) {
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(any(), any())
            .thenReturn(result)
    }
}
