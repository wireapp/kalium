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

package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionResponse
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.util.time.UNIX_FIRST_DATE
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.wire.kalium.network.api.base.model.UserId as NetworkUserId

class ConnectionRepositoryTest {

    @Test
    fun givenConnections_whenFetchingConnections_thenConnectionsAreInsertedOrUpdatedIntoDatabase() = runTest {
        // given
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withSuccessfulGetConversationById(arrangement.stubConversationID1)
            .withSuccessfulGetConversationById(arrangement.stubConversationID2)

        // when
        val result = connectionRepository.fetchSelfUserConnections()

        // then
        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::updateOrInsertOneOnOneMember)
            .with(any(), any())
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

        // when
        val result = connectionRepository.fetchSelfUserConnections()

        // then
        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::updateOrInsertOneOnOneMember)
            .with(any(), any())
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
            .withSelfUserTeamId(Either.Right(TestUser.SELF.teamId))
            .withFetchSentConversationSucceed()

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        result.shouldSucceed()
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::createConnection)
            .with(eq(userId))
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
            .withFetchSentConversationSucceed()

        // when
        val result = connectionRepository.sendUserConnection(UserId(userId.value, userId.domain))

        // then
        result.shouldFail()
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::createConnection)
            .with(eq(userId))
            .wasInvoked(once)
        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::updateOrInsertOneOnOneMember)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversations)
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
            .withSelfUserTeamId(Either.Right(TestUser.SELF.teamId))
            .withFetchSentConversationSucceed()

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
            .withSelfUserTeamId(Either.Right(TestUser.SELF.teamId))

        // when
        val result = connectionRepository.updateConnectionStatus(UserId(userId.value, userId.domain), ConnectionState.ACCEPTED)
        result.shouldSucceed { arrangement.stubConnectionOne }

        // then
        verify(arrangement.connectionApi)
            .suspendFunction(arrangement.connectionApi::updateConnection)
            .with(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
            .wasInvoked(once)
        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::updateOrInsertOneOnOneMember)
            .with(any(), any())
            .wasInvoked(exactly = once)
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
        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::updateOrInsertOneOnOneMember)
            .with(any(), any())
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
        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::updateOrInsertOneOnOneMember)
            .with(any(), any())
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
        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::updateOrInsertOneOnOneMember)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationId_WhenDeletingConnection_thenDeleteConnectionDataAndConversationShouldBeTriggered() = runTest {
        // given
        val conversationId = com.wire.kalium.logic.data.id.QualifiedID("conversation_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement.withDeleteConnectionDataAndConversation(conversationId.toDao())

        // when
        val result = connectionRepository.deleteConnection(conversationId)

        // then
        result.shouldSucceed()
        verify(arrangement.connectionDAO)
            .suspendFunction(arrangement.connectionDAO::deleteConnectionDataAndConversation)
            .with(eq(conversationId.toDao()))
            .wasInvoked(once)
    }

    private class Arrangement :
        MemberDAOArrangement by MemberDAOArrangementImpl() {
        @Mock
        val conversationDAO = configure(mock(classOf<ConversationDAO>())) { stubsUnitByDefault = true }

        @Mock
        val conversationRepository = configure(mock(classOf<ConversationRepository>())) { stubsUnitByDefault = true }

        @Mock
        val connectionDAO = configure(mock(classOf<ConnectionDAO>())) { stubsUnitByDefault = true }

        @Mock
        val connectionApi = mock(classOf<ConnectionApi>())

        @Mock
        val userDetailsApi = mock(classOf<UserDetailsApi>())

        @Mock
        val userDAO = mock(classOf<UserDAO>())

        @Mock
        val selfTeamIdProvider = mock(classOf<SelfTeamIdProvider>())

        val connectionRepository = ConnectionDataSource(
            conversationDAO = conversationDAO,
            connectionApi = connectionApi,
            connectionDAO = connectionDAO,
            userDAO = userDAO,
            memberDAO = memberDAO,
            conversationRepository = conversationRepository
        )

        val stubConnectionOne = ConnectionDTO(
            conversationId = "conversationId1",
            from = "fromId",
            lastUpdate = UNIX_FIRST_DATE,
            qualifiedConversationId = ConversationId("conversationId1", "domain"),
            qualifiedToId = NetworkUserId("connectionId1", "domain"),
            status = ConnectionStateDTO.ACCEPTED,
            toId = "connectionId1"
        )
        val stubConnectionTwo = ConnectionDTO(
            conversationId = "conversationId2",
            from = "fromId",
            lastUpdate = UNIX_FIRST_DATE,
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
            legalHoldStatus = LegalHoldStatusDTO.ENABLED,
            teamId = "team",
            assets = emptyList(),
            deleted = null,
            email = null,
            expiresAt = null,
            nonQualifiedId = "value",
            service = null,
            supportedProtocols = null
        )

        val stubUserEntity = TestUser.DETAILS_ENTITY
        val stubConversationID1 = QualifiedIDEntity("conversationId1", "domain")
        val stubConversationID2 = QualifiedIDEntity("conversationId2", "domain")

        fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>): Arrangement {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .then { either }
            return this
        }

        fun withFetchSentConversationSucceed(): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchSentConnectionConversation)
                .whenInvokedWith(any())
                .then { Either.Right(Unit) }
            return this
        }

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
                .then { flowOf(TestConversation.VIEW_ENTITY) }

            return this
        }

        fun withNotFoundGetConversationError(): Arrangement = apply {
            // TODO: use withUpdateOrInsertOneOnOneMemberFailure directly in the test once it is fully refactored
            withUpdateOrInsertOneOnOneMemberFailure(
                error = Exception("error"),
                member = any(),
                conversationId = any()
            )
        }

        fun withErrorOnCreateConnectionResponse(userId: NetworkUserId): Arrangement {
            given(connectionApi)
                .suspendFunction(connectionApi::createConnection)
                .whenInvokedWith(eq(userId))
                .then { NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))) }

            return this
        }

        fun withErrorOnPersistingConnectionResponse(userId: NetworkUserId): Arrangement = apply {
            // TODO: use withUpdateOrInsertOneOnOneMemberFailure directly in the test once it is fully refactored
            withUpdateOrInsertOneOnOneMemberFailure(
                error = RuntimeException("An error occurred persisting the data"),
                member = eq(UserIDEntity(userId.value, userId.domain)),
                conversationId = any()
            )
        }

        fun withSuccessfulUpdateConnectionStatusResponse(userId: NetworkUserId): Arrangement = apply {
            given(connectionApi)
                .suspendFunction(connectionApi::updateConnection)
                .whenInvokedWith(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
                .then { _, _ -> NetworkResponse.Success(stubConnectionOne, mapOf(), 200) }

            withUpdateOrInsertOneOnOneMemberSuccess(
                member = eq(UserIDEntity(userId.value, userId.domain)),
                conversationId = any()
            )
        }

        fun withErrorUpdatingConnectionStatusResponse(userId: NetworkUserId): Arrangement = apply {
            given(connectionApi)
                .suspendFunction(connectionApi::updateConnection)
                .whenInvokedWith(eq(userId), any())
                .then { _, _ -> NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))) }
        }

        fun withDeleteConnectionDataAndConversation(conversationId: QualifiedIDEntity): Arrangement = apply {
            given(connectionDAO)
                .suspendFunction(connectionDAO::deleteConnectionDataAndConversation)
                .whenInvokedWith(eq(conversationId))
                .thenReturn(Unit)
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

            withUpdateOrInsertOneOnOneMemberSuccess()

            given(userDAO).suspendFunction(userDAO::observeUserDetailsByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(stubUserEntity) }

            given(userDAO)
                .suspendFunction(userDAO::upsertUser)
                .whenInvokedWith(any())
                .then { }

            return this
        }

        fun withSuccessfulGetUserById(id: QualifiedIDEntity): Arrangement {
            given(userDAO)
                .suspendFunction(userDAO::observeUserDetailsByQualifiedID)
                .whenInvokedWith(eq(id))
                .then { flowOf(stubUserEntity) }

            return this
        }

        fun arrange() = this to connectionRepository
    }
}
