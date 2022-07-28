package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.shouldFail
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
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import com.wire.kalium.network.api.UserId as NetworkUserId

class ConnectionRepositoryTest {

    @Test
    fun givenConnections_whenFetchingConnections_thenConnectionsAreInsertedOrUpdatedIntoDatabase() = runTest {
        // given
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withSuccessfulGetConversationById(arrangement.stubConversationID1)
            .withSuccessfulGetConversationById(arrangement.stubConversationID2)

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
    fun givenConnections_whenFetchingConnections_thenConnectionsAreInsertedOrUpdatedIntoDatabaseOnlyIfConversationsAreFound() = runTest {
        // given
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withNotFoundGetConversationError()
            .withSuccessfulGetConversationById(arrangement.stubConversationID1)

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
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withSuccessfulGetConversationById(arrangement.stubConversationID1)
            .withSuccessfulCreateConnectionResponse(userId)

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        result.shouldSucceed()
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::insertUser)
            .with(any())
            .wasInvoked(once)
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getUserInfo)
            .with(any())
            .wasInvoked(once)
        verify(arrangement.connectionDAO)
            .suspendFunction(arrangement.connectionDAO::updateConnectionLastUpdatedTime)
            .with(any(), any())
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnectionAndReturnsAnError_thenTheConnectionShouldNotBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withErrorOnCreateConnectionResponse(userId)

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        result.shouldFail()
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnectionAndPersistingReturnsAnError_thenTheConnectionShouldNotBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withSuccessfulGetUserById(arrangement.stubUserEntity.id)
            .withSuccessfulCreateConnectionResponse(userId)
            .withSuccessfulGetConversationById(arrangement.stubConversationID1)
            .withErrorOnPersistingConnectionResponse(userId)

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(arrangement.connectionDAO)
            .suspendFunction(arrangement.connectionDAO::insertConnection)
            .with(any())
            .wasInvoked(once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::insertUser)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequestUpdate_WhenSendingAConnectionStatusValid_thenTheConnectionShouldBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulUpdateConnectionStatusResponse(userId)
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withSuccessfulGetConversationById(arrangement.stubConversationID1)

        // when
        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.ACCEPTED)
        result.shouldSucceed { arrangement.stubConnectionOne }

        // then
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::updateConnection)
            .with(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
            .wasInvoked(once)
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequestUpdate_WhenSendingAConnectionStatusInvalid_thenTheConnectionShouldThrowAnError() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement.withSuccessfulUpdateConnectionStatusResponse(userId)

        // when
        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.NOT_CONNECTED)

        // then
        result.shouldFail {}
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::updateConnection)
            .with(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
            .wasNotInvoked()
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAConnectionRequestUpdate_WhenSendingAConnectionStatusFails_thenShouldThrowAFailure() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement.withErrorUpdatingConnectionStatusResponse(userId)

        // when
        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.ACCEPTED)

        // then
        result.shouldFail {}
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::updateConnection)
            .with(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
            .wasInvoked(once)
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAConnectionRequestUpdate_WhenSendingAnInvalidConnectionStatusFails_thenShouldThrowAFailure() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement.withErrorUpdatingConnectionStatusResponse(userId)


        // when
        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.PENDING)

        // then
        result.shouldFail {}
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::updateConnection)
            .with(eq(userId), eq(ConnectionStateDTO.PENDING))
            .wasNotInvoked()
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

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

        @Mock
        val userMapper = mock(classOf<UserMapper>())

        @Mock
        val metaDAO = mock(classOf<MetadataDAO>())

        val connectionRepository = ConnectionDataSource(
            conversationDAO = conversationDAO,
            connectionApi = connectionApi,
            connectionDAO = connectionDAO,
            userDetailsApi = userDetailsApi,
            userDAO = userDAO,
            metadataDAO = metaDAO,
            userMapper = userMapper
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

        val stubJsonQualifiedId = """{"value":"test" , "domain":"test" }"""

        val stubUserEntity = UserEntity(
            id = QualifiedIDEntity("value", "domain"),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            team = null,
            connectionStatus = ConnectionEntity.State.NOT_CONNECTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.AVAILABLE,
            userType = UserTypeEntity.EXTERNAL
        )

        val stubSelfUser = SelfUser(
            id = com.wire.kalium.logic.data.id.QualifiedID("someValue", "someId"),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
        )

        val stubConversationID1 = QualifiedIDEntity("conversationId1", "domain")
        val stubConversationID2 = QualifiedIDEntity("conversationId2", "domain")

        fun withSuccessfulCreateConnectionResponse(userId: NetworkUserId): Arrangement {
            given(connectionApi)
                .suspendFunction(connectionApi::createConnection)
                .whenInvokedWith(eq(userId))
                .then { NetworkResponse.Success(stubConnectionOne, mapOf(), 200) }

            return this
        }

        fun withSuccessfulGetConversationById(conversationId: QualifiedIDEntity): Arrangement {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
                .whenInvokedWith(eq(conversationId))
                .then { flowOf(TestConversation.ENTITY) }

            return this
        }

        fun withNotFoundGetConversationError(): Arrangement {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
                .whenInvokedWith(any(), any(), any())
                .thenThrow(Exception("error"))

            return this
        }

        fun withErrorOnCreateConnectionResponse(userId: NetworkUserId): Arrangement {
            given(connectionApi)
                .suspendFunction(connectionApi::createConnection)
                .whenInvokedWith(eq(userId))
                .then { NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))) }

            return this
        }

        fun withErrorOnPersistingConnectionResponse(userId: NetworkUserId): Arrangement {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
                .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
                .thenThrow(RuntimeException("An error occurred persisting the data"))

            return this
        }

        fun withSuccessfulUpdateConnectionStatusResponse(userId: NetworkUserId): Arrangement {
            given(connectionApi)
                .suspendFunction(connectionApi::updateConnection)
                .whenInvokedWith(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
                .then { _, _ -> NetworkResponse.Success(stubConnectionOne, mapOf(), 200) }
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
                .whenInvokedWith(eq(UserIDEntity(userId.value, userId.domain)), any(), any())
                .then { _, _, _ -> return@then }

            return this
        }

        fun withErrorUpdatingConnectionStatusResponse(userId: NetworkUserId): Arrangement {
            given(connectionApi)
                .suspendFunction(connectionApi::updateConnection)
                .whenInvokedWith(eq(userId), any())
                .then { _, _ -> NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))) }

            return this
        }

        fun withSuccessfulFetchSelfUserConnectionsResponse(stubUserProfileDTO: UserProfileDTO): Arrangement {
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

            given(metaDAO)
                .suspendFunction(metaDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .then { flowOf(stubJsonQualifiedId) }

            given(userDAO).suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(stubUserEntity) }

            given(userMapper)
                .function(userMapper::fromDaoModelToSelfUser)
                .whenInvokedWith(any())
                .then { stubSelfUser }
            given(userDAO)
                .suspendFunction(userDAO::insertUser)
                .whenInvokedWith(any())
                .then { }

            return this
        }

        fun withSuccessfulGetUserById(id: QualifiedIDEntity): Arrangement {
            given(userDAO)
                .suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(eq(id))
                .then { flowOf(stubUserEntity) }

            return this
        }


        fun arrange() = this to connectionRepository
    }
}
