package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.user.connection.Connection
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionResponse
import com.wire.kalium.network.api.user.connection.ConnectionState
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ConnectionRepositoryTest {

    @Mock
    private val conversationDAO = configure(mock(classOf<ConversationDAO>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val connectionApi = mock(classOf<ConnectionApi>())

    private lateinit var connectionRepository: ConnectionRepository

    @BeforeTest
    fun setUp() {
        connectionRepository = ConnectionDataSource(
            conversationDAO = conversationDAO,
            connectionApi = connectionApi
        )
    }

    @Test
    fun givenConnections_whenFetchingConnections_thenConnectionsAreInsertedOrUpdatedIntoDatabase() = runTest {

        given(connectionApi)
            .suspendFunction(connectionApi::fetchSelfUserConnections)
            .whenInvokedWith(eq(null))
            .then {
                NetworkResponse.Success(
                    connectionsResponse,
                    mapOf(),
                    200
                )
            }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertOrUpdateOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(any(), any(), any())

        val result = connectionRepository.fetchSelfUserConnections()

        // Verifies that conversationDAO was called the same amount there is connections
        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertOrUpdateOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasInvoked(exactly = twice)

        // Verifies that when fetching connections, it succeeded
        result.shouldSucceed()
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnection_thenTheConnectionShouldBeSentAndPersistedLocally() = runTest {
        // given
        val userId = UserId("user_id", "domain_id")
        given(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .whenInvokedWith(eq(userId))
            .then { NetworkResponse.Success(connection1, mapOf(), 200) }
        given(conversationDAO)
            .suspendFunction(conversationDAO::insertOrUpdateOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
            .then { _, _, _ -> return@then }

        // when
        val result = connectionRepository.sendUserConnection(com.wire.kalium.logic.data.user.UserId("user_id", "domain_id"))

        // then
        result.shouldSucceed()
        verify(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertOrUpdateOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasInvoked(once)
    }

    private companion object {
        val connection1 = Connection(
            conversationId = "conversationId1",
            from = "fromId",
            lastUpdate = "lastUpdate",
            qualifiedConversationId = ConversationId("conversationId1", "domain"),
            qualifiedToId = UserId("connectionId1", "domain"),
            status = ConnectionState.ACCEPTED,
            toId = "connectionId1"
        )
        val connection2 = Connection(
            conversationId = "conversationId2",
            from = "fromId",
            lastUpdate = "lastUpdate",
            qualifiedConversationId = ConversationId("conversationId2", "domain"),
            qualifiedToId = UserId("connectionId2", "domain"),
            status = ConnectionState.ACCEPTED,
            toId = "connectionId2"
        )
        val connectionsResponse = ConnectionResponse(
            connections = listOf(connection1, connection2),
            hasMore = false,
            pagingState = ""
        )
    }
}
