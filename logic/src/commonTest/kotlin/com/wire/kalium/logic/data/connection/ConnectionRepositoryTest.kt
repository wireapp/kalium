package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionDTO
import com.wire.kalium.network.api.user.connection.ConnectionResponse
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
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
import kotlin.test.Test
import com.wire.kalium.network.api.UserId as NetworkUserId

class ConnectionRepositoryTest {

    @Test
    fun givenConnections_whenFetchingConnections_thenConnectionsAreInsertedOrUpdatedIntoDatabase() = runTest {
        // given
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement.withSuccessfulResponse()

        //when
        val result = connectionRepository.fetchSelfUserConnections()

        // then
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasInvoked(exactly = twice)

        // Verifies that when fetching connections, it succeeded
        result.shouldSucceed()
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnection_thenTheConnectionShouldBeSentAndPersistedLocally() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulResponse()
            .withSuccessfulCreateConnectionResponse(userId)

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        result.shouldSucceed()
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), eq(ConnectionEntity.State.SENT), any())
            .wasInvoked(once)
    }
//
//    @Test
//    fun givenAConnectionRequest_WhenSendingAConnectionAndReturnsAnError_thenTheConnectionShouldNotBePersisted() = runTest {
//        // given
//        val userId = NetworkUserId("user_id", "domain_id")
//        given(connectionApi)
//            .suspendFunction(connectionApi::createConnection)
//            .whenInvokedWith(eq(userId))
//            .then { NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))) }
//        given(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
//            .then { _, _, _ -> return@then }
//
//        // when
//        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))
//
//        // then
//        result.shouldFail()
//        verify(connectionApi)
//            .suspendFunction(connectionApi::createConnection)
//            .with(eq(userId))
//            .wasInvoked(once)
//        verify(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .with(any(), any(), any())
//            .wasNotInvoked()
//    }
//
//    @Test
//    fun givenAConnectionRequest_WhenSendingAConnectionAndPersistingReturnsAnError_thenTheConnectionShouldNotBePersisted() = runTest {
//        // given
//        val userId = NetworkUserId("user_id", "domain_id")
//        given(connectionApi)
//            .suspendFunction(connectionApi::createConnection)
//            .whenInvokedWith(eq(userId))
//            .then { NetworkResponse.Success(connection1, mapOf(), 200) }
//        given(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
//            .thenThrow(RuntimeException("An error occurred persisting the data"))
//        given(userDetailsApi)
//            .suspendFunction(userDetailsApi::getUserInfo)
//            .whenInvokedWith(any())
//            .then { NetworkResponse.Success(userProfileDto, mapOf(), 200) }
//        given(userDAO)
//            .suspendFunction(userDAO::insertUser)
//            .whenInvokedWith(any())
//            .then { }
//
//        // when
//        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))
//
//        // then
//        verify(connectionApi)
//            .suspendFunction(connectionApi::createConnection)
//            .with(eq(userId))
//            .wasInvoked(once)
//        verify(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .with(any(), any(), any())
//            .wasInvoked(once)
//        verify(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .with(any(), any(), any())
//            .wasNotInvoked()
//    }
//
//    @Test
//    fun givenAConnectionRequestUpdate_WhenSendingAConnectionStatusValid_thenTheConnectionShouldBePersisted() = runTest {
//        // given
//        val userId = NetworkUserId("user_id", "domain_id")
//        given(connectionApi)
//            .suspendFunction(connectionApi::updateConnection)
//            .whenInvokedWith(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
//            .then { _, _ -> NetworkResponse.Success(connection1, mapOf(), 200) }
//        given(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
//            .then { _, _, _ -> return@then }
//
//        // when
//        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.ACCEPTED)
//        result.shouldSucceed()
//
//        // then
//        verify(connectionApi)
//            .suspendFunction(connectionApi::updateConnection)
//            .with(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
//            .wasInvoked(once)
//        verify(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .with(any(), any(), any())
//            .wasInvoked(once)
//    }
//
//    @Test
//    fun givenAConnectionRequestUpdate_WhenSendingAConnectionStatusInvalid_thenTheConnectionShouldThrowAnError() = runTest {
//        // given
//        val userId = NetworkUserId("user_id", "domain_id")
//        given(connectionApi)
//            .suspendFunction(connectionApi::updateConnection)
//            .whenInvokedWith(eq(userId), any())
//            .then { _, _ -> NetworkResponse.Success(connection1, mapOf(), 200) }
//        given(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
//            .then { _, _, _ -> return@then }
//
//        // when
//        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.NOT_CONNECTED)
//
//        // then
//        result.shouldFail()
//        verify(connectionApi)
//            .suspendFunction(connectionApi::updateConnection)
//            .with(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
//            .wasNotInvoked()
//        verify(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .with(any(), any(), any())
//            .wasNotInvoked()
//    }
//
//    @Test
//    fun givenAConnectionRequestUpdate_WhenSendingAConnectionStatusFails_thenShouldThrowAFailure() = runTest {
//        // given
//        val userId = NetworkUserId("user_id", "domain_id")
//        given(connectionApi)
//            .suspendFunction(connectionApi::updateConnection)
//            .whenInvokedWith(eq(userId), any())
//            .then { _, _ -> NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))) }
//        given(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
//            .then { _, _, _ -> return@then }
//
//        // when
//        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.ACCEPTED)
//
//        // then
//        result.shouldFail()
//        verify(connectionApi)
//            .suspendFunction(connectionApi::updateConnection)
//            .with(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
//            .wasInvoked(once)
//        verify(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .with(any(), any(), any())
//            .wasNotInvoked()
//    }
//
//    @Test
//    fun givenAConnectionRequestUpdate_WhenSendingAnInvalidConnectionStatusFails_thenShouldThrowAFailure() = runTest {
//        // given
//        val userId = NetworkUserId("user_id", "domain_id")
//
//        // when
//        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.PENDING)
//
//        // then
//        result.shouldFail()
//        verify(connectionApi)
//            .suspendFunction(connectionApi::updateConnection)
//            .with(eq(userId), eq(ConnectionStateDTO.PENDING))
//            .wasNotInvoked()
//        verify(conversationDAO)
//            .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
//            .with(any(), any(), any())
//            .wasNotInvoked()
//    }

    private class Arrangement {
        @Mock
        val conversationDAO = configure(mock(classOf<ConversationDAO>())) { stubsUnitByDefault = true }

        @Mock
        val connectionDAO = configure(mock(classOf<ConnectionDAO>())) { stubsUnitByDefault = true }

        @Mock
        val connectionApi = mock(classOf<ConnectionApi>())

        @Mock
        val userDetailsApi = mock(classOf<UserDetailsApi>())

        @Mock
        val userDAO = mock(classOf<UserDAO>())

        val connectionRepository = ConnectionDataSource(
            conversationDAO = conversationDAO,
            connectionApi = connectionApi,
            connectionDAO = connectionDAO,
            userDetailsApi = userDetailsApi,
            userDAO = userDAO
        )

        val stubConnectionOne = ConnectionDTO(
            conversationId = "conversationId1",
            from = "fromId",
            lastUpdate = "lastUpdate",
            qualifiedConversationId = ConversationId("conversationId1", "domain"),
            qualifiedToId = NetworkUserId("connectionId1", "domain"),
            status = ConnectionStateDTO.ACCEPTED,
            toId = "connectionId1"
        )
        val stubConnectionTwo = ConnectionDTO(
            conversationId = "conversationId2",
            from = "fromId",
            lastUpdate = "lastUpdate",
            qualifiedConversationId = ConversationId("conversationId2", "domain"),
            qualifiedToId = NetworkUserId("connectionId2", "domain"),
            status = ConnectionStateDTO.ACCEPTED,
            toId = "connectionId2"
        )
        val stubConnectionResponse = ConnectionResponse(
            connections = listOf(stubConnectionOne, stubConnectionTwo),
            hasMore = false,
            pagingState = ""
        )
        val stubUserProfileDTO = UserProfileDTO(
            accentId = 1,
            handle = "handle",
            id = QualifiedID(value = "value", domain = "domain"),
            name = "name",
            legalHoldStatus = LegalHoldStatusResponse.ENABLED,
            teamId = "team",
            assets = emptyList(),
            deleted = null,
            email = null,
            expiresAt = null,
            nonQualifiedId = "value",
            service = null
        )

        fun withSuccessfulCreateConnectionResponse(userId: com.wire.kalium.network.api.UserId): Arrangement {
            given(connectionApi)
                .suspendFunction(connectionApi::createConnection)
                .whenInvokedWith(eq(userId))
                .then { NetworkResponse.Success(stubConnectionOne, mapOf(), 200) }

            return this
        }

        fun withSuccessfulResponse(): Arrangement {

            given(connectionApi)
                .suspendFunction(connectionApi::fetchSelfUserConnections)
                .whenInvokedWith(eq(null))
                .then {
                    NetworkResponse.Success(
                        stubConnectionResponse,
                        mapOf(),
                        200
                    )
                }
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getUserInfo)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(stubUserProfileDTO, mapOf(), 200) }
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
                .whenInvokedWith(any(), any(), any())
            given(userDAO)
                .suspendFunction(userDAO::insertUser)
                .whenInvokedWith(any())
                .then { }

            return this
        }


        fun arrange() = this to connectionRepository
    }
}
