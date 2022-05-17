package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.user.connection.Connection
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionResponse
import com.wire.kalium.network.api.user.connection.ConnectionState
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserEntity
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
import com.wire.kalium.network.api.UserId as NetworkUserId

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
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(any(), any(), any())

        val result = connectionRepository.fetchSelfUserConnections()

        // Verifies that conversationDAO was called the same amount there is connections
        verify(conversationDAO)
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasInvoked(exactly = twice)

        // Verifies that when fetching connections, it succeeded
        result.shouldSucceed()
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnection_thenTheConnectionShouldBeSentAndPersistedLocally() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        given(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .whenInvokedWith(eq(userId))
            .then { NetworkResponse.Success(connection1, mapOf(), 200) }
        given(conversationDAO)
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
            .then { _, _, _ -> return@then }

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        result.shouldSucceed()
        verify(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(conversationDAO)
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), eq(UserEntity.ConnectionState.SENT), any())
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnectionAndReturnsAnError_thenTheConnectionShouldNotBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        given(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .whenInvokedWith(eq(userId))
            .then { NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))) }
        given(conversationDAO)
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
            .then { _, _, _ -> return@then }

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        result.shouldFail()
        verify(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(conversationDAO)
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnectionAndPersistingReturnsAnError_thenTheConnectionShouldNotBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        given(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .whenInvokedWith(eq(userId))
            .then { NetworkResponse.Success(connection1, mapOf(), 200) }
        given(conversationDAO)
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
            .thenThrow(RuntimeException("An error occurred persisting the data"))

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        verify(connectionApi)
            .suspendFunction(connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(conversationDAO)
            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasInvoked(once)
    }

    private companion object {
        val connection1 = Connection(
            conversationId = "conversationId1",
            from = "fromId",
            lastUpdate = "lastUpdate",
            qualifiedConversationId = ConversationId("conversationId1", "domain"),
            qualifiedToId = NetworkUserId("connectionId1", "domain"),
            status = ConnectionState.ACCEPTED,
            toId = "connectionId1"
        )
        val connection2 = Connection(
            conversationId = "conversationId2",
            from = "fromId",
            lastUpdate = "lastUpdate",
            qualifiedConversationId = ConversationId("conversationId2", "domain"),
            qualifiedToId = NetworkUserId("connectionId2", "domain"),
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
