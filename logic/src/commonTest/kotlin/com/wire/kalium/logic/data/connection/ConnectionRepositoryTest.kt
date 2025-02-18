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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.authenticated.connection.ConnectionResponse
import com.wire.kalium.network.api.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.FederationUnreachableResponse
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.PredicateMatcher
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.wire.kalium.network.api.model.UserId as NetworkUserId

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
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasInvoked(exactly = twice)

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
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasInvoked(exactly = twice)

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
        coVerify {
            arrangement.connectionApi.createConnection(eq(userId))
        }.wasInvoked(once)
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
        coVerify {
            arrangement.connectionApi.createConnection(eq(userId))
        }.wasInvoked(once)
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.conversationRepository.fetchConversations()
        }.wasNotInvoked()
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
        coVerify {
            arrangement.connectionApi.createConnection(eq(userId))
        }.wasInvoked(once)
        coVerify {
            arrangement.connectionDAO.insertConnection(any())
        }.wasInvoked(once)
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
        coVerify {
            arrangement.connectionApi.updateConnection(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
        }.wasInvoked(once)
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.connectionApi.updateConnection(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
        }.wasNotInvoked()
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.connectionApi.updateConnection(eq(userId), eq(ConnectionStateDTO.ACCEPTED))
        }.wasInvoked(once)
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.connectionApi.updateConnection(eq(userId), eq(ConnectionStateDTO.PENDING))
        }.wasNotInvoked()
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationId_WhenDeletingConnection_thenDeleteConnectionDataAndConversationShouldBeTriggered() = runTest {
        // given
        val conversationId = TestConversation.ID
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement.withDeleteConnectionDataAndConversation(conversationId.toDao())
        val connection = TestConnection.CONNECTION.copy(
            conversationId = conversationId.value,
            qualifiedConversationId = conversationId,
        )

        // when
        val result = connectionRepository.deleteConnection(connection)

        // then
        result.shouldSucceed()
        coVerify {
            arrangement.connectionDAO.deleteConnectionDataAndConversation(eq(conversationId.toDao()))
        }.wasInvoked(once)
        coVerify {
            arrangement.userDAO.upsertConnectionStatuses(eq(mapOf(connection.qualifiedToId.toDao() to ConnectionEntity.State.CANCELLED)))
        }.wasInvoked(once)
    }

    @Test
    fun givenConnectionExists_whenGettingConnection_thenConnectionShouldBeReturned() = runTest {
        // given
        val (arrangement, connectionRepository) = Arrangement().arrange()
        val connection = arrangement.stubConnectionEntity
        arrangement.withGetConnection(connection)
        // when
        val result = connectionRepository.getConnection(connection.qualifiedConversationId.toModel())
        // then
        result.shouldSucceed {
            assertEquals(connection.qualifiedConversationId.toModel(), it.conversationId)
        }
        coVerify {
            arrangement.connectionDAO.getConnection(eq(connection.qualifiedConversationId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequestIgnore_WhenSendingAConnectionStatusValid_thenTheConnectionShouldBeUpdated() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulUpdateConnectionStatusResponse(userId)
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)

        // when
        val result = connectionRepository.ignoreConnectionRequest(UserId(userId.value, userId.domain))
        result.shouldSucceed { arrangement.stubConnectionOne }

        // then
        coVerify {
            arrangement.connectionApi.updateConnection(userId, ConnectionStateDTO.IGNORED)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConnectionRequestIgnore_WhenSendingAConnectionStatusValid_thenTheConnectionShouldBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withGetConnectionByUser()
            .withSuccessfulUpdateConnectionStatusResponse(userId)
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)

        // when
        val result = connectionRepository.ignoreConnectionRequest(UserId(userId.value, userId.domain))
        result.shouldSucceed { arrangement.stubConnectionOne }

        // then
        coVerify {
            arrangement.connectionDAO.insertConnection(arrangement.stubConnectionEntity.copy(
                lastUpdateDate = any(),
                status = ConnectionEntity.State.IGNORED
            ))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConnectionDoesNotExist_whenGettingConnection_thenErrorNotFoundShouldBeReturned() = runTest {
        // given
        val (arrangement, connectionRepository) = Arrangement().arrange()
        val connection = arrangement.stubConnectionEntity
        arrangement.withGetConnection(null)
        // when
        val result = connectionRepository.getConnection(connection.qualifiedConversationId.toModel())
        // then
        result.shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }
        coVerify {
            arrangement.connectionDAO.getConnection(eq(connection.qualifiedConversationId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequestIgnore_WhenApiUpdateFailedWithFederatedFailedDomains_thenTheConnectionShouldBeUpdated() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withErrorUpdatingConnectionStatusResponse(
                userId,
                KaliumException.FederationUnreachableException(FederationUnreachableResponse())
            )
            .withConnectionEntityByUser()
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)

        // when
        val result = connectionRepository.ignoreConnectionRequest(UserId(userId.value, userId.domain))
        result.shouldSucceed { arrangement.stubConnectionOne }

        // then
        coVerify {
            arrangement.connectionApi.updateConnection(userId, ConnectionStateDTO.IGNORED)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConnectionRequestIgnore_WhenApiUpdateFailedWithFederatedFailedDomains_thenTheConnectionShouldBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withErrorUpdatingConnectionStatusResponse(
                userId,
                KaliumException.FederationUnreachableException(FederationUnreachableResponse())
            )
            .withConnectionEntityByUser()
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)

        // when
        val result = connectionRepository.ignoreConnectionRequest(UserId(userId.value, userId.domain))
        result.shouldSucceed { arrangement.stubConnectionOne }

        // then
        coVerify {
            arrangement.connectionDAO.insertConnection(arrangement.stubConnectionEntity.copy(
                lastUpdateDate = any(),
                status = ConnectionEntity.State.IGNORED
            ))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConnectionRequestIgnore_WhenApiUpdateFailedWithNonFederatedFailedDomains_thenTheConnectionNotShouldBePersisted() =
        runTest {
            // given
            val userId = NetworkUserId("user_id", "domain_id")
            val (arrangement, connectionRepository) = Arrangement().arrange()
            arrangement
                .withErrorUpdatingConnectionStatusResponse(userId)
                .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)

            // when
            val result = connectionRepository.ignoreConnectionRequest(UserId(userId.value, userId.domain))
            result.shouldFail { arrangement.stubConnectionOne }

            // then
            coVerify {
                arrangement.connectionDAO.insertConnection(arrangement.stubConnectionEntity.copy(
                    status = ConnectionEntity.State.IGNORED
                ))
            }.wasInvoked(exactly = 0)
        }

    private class Arrangement :
        MemberDAOArrangement by MemberDAOArrangementImpl() {
        @Mock
        val conversationDAO = mock(ConversationDAO::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val connectionDAO = mock(ConnectionDAO::class)

        @Mock
        val connectionApi = mock(ConnectionApi::class)

        @Mock
        val userDetailsApi = mock(UserDetailsApi::class)

        @Mock
        val userDAO = mock(UserDAO::class)

        @Mock
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

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
            lastUpdate = Instant.UNIX_FIRST_DATE,
            qualifiedConversationId = ConversationId("conversationId1", "domain"),
            qualifiedToId = NetworkUserId("connectionId1", "domain"),
            status = ConnectionStateDTO.ACCEPTED,
            toId = "connectionId1"
        )
        val stubConnectionTwo = ConnectionDTO(
            conversationId = "conversationId2",
            from = "fromId",
            lastUpdate = Instant.UNIX_FIRST_DATE,
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
        val stubConnectionEntity = ConnectionEntity(
            conversationId = "conversationId1",
            from = "fromId",
            lastUpdateDate = Instant.UNIX_FIRST_DATE,
            qualifiedConversationId = ConversationIDEntity("conversationId", "domain"),
            qualifiedToId = ConversationIDEntity("userId", "domain"),
            status = ConnectionEntity.State.ACCEPTED,
            toId = "connectionId1"
        )

        val stubUserEntity = TestUser.DETAILS_ENTITY
        val stubConversationID1 = QualifiedIDEntity("conversationId1", "domain")
        val stubConversationID2 = QualifiedIDEntity("conversationId2", "domain")
        val connectionEntity = ConnectionEntity(
            conversationId = "conversationId1",
            from = "fromId",
            lastUpdateDate = Instant.DISTANT_PAST,
            qualifiedConversationId = ConversationIDEntity("conversationId1", "domain"),
            qualifiedToId = QualifiedIDEntity("connectionId1", "domain"),
            status = ConnectionEntity.State.ACCEPTED,
            toId = "connectionId1"
        )

        suspend fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>): Arrangement {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(either)
            return this
        }

        suspend fun withFetchSentConversationSucceed(): Arrangement {
            coEvery {
                conversationRepository.fetchSentConnectionConversation(any())
            }.returns(Either.Right(Unit))
            return this
        }

        suspend fun withSuccessfulCreateConnectionResponse(userId: NetworkUserId): Arrangement {
            coEvery {
                connectionApi.createConnection(eq(userId))
            }.returns(NetworkResponse.Success(stubConnectionOne, mapOf(), 200))

            return this
        }

        suspend fun withSuccessfulGetConversationById(conversationId: QualifiedIDEntity): Arrangement {
            coEvery {
                conversationDAO.observeConversationDetailsById(eq(conversationId))
            }.returns(flowOf(TestConversation.VIEW_ENTITY))

            return this
        }

        suspend fun withNotFoundGetConversationError(): Arrangement = apply {
            // TODO: use withUpdateOrInsertOneOnOneMemberFailure directly in the test once it is fully refactored
            withUpdateOrInsertOneOnOneMemberFailure(
                error = Exception("error"),
                member = AnyMatcher(valueOf()),
                conversationId = AnyMatcher(valueOf())
            )
        }

        suspend fun withErrorOnCreateConnectionResponse(userId: NetworkUserId): Arrangement {
            coEvery {
                connectionApi.createConnection(eq(userId))
            }.returns(NetworkResponse.Error(KaliumException.GenericError(RuntimeException("An error the server threw!"))))

            return this
        }

        suspend fun withErrorOnPersistingConnectionResponse(userId: NetworkUserId): Arrangement = apply {
            // TODO: use withUpdateOrInsertOneOnOneMemberFailure directly in the test once it is fully refactored
            withUpdateOrInsertOneOnOneMemberFailure(
                error = RuntimeException("An error occurred persisting the data"),
                member = PredicateMatcher(
                    MemberEntity::class,
                    valueOf()
                ) { it.user == QualifiedIDEntity(userId.value, userId.domain) },
                conversationId = AnyMatcher(valueOf())
            )
        }

        suspend fun withSuccessfulUpdateConnectionStatusResponse(userId: NetworkUserId): Arrangement = apply {
            coEvery {
                connectionApi.updateConnection(eq(userId), any())
            }.returns(NetworkResponse.Success(stubConnectionOne, mapOf(), 200))

            withUpdateOrInsertOneOnOneMemberSuccess(
                member = PredicateMatcher(
                    MemberEntity::class,
                    valueOf()
                ) { it.user == QualifiedIDEntity(userId.value, userId.domain) },
                conversationId = AnyMatcher(valueOf())
            )
        }

        suspend fun withErrorUpdatingConnectionStatusResponse(
            userId: NetworkUserId,
            exception: KaliumException = KaliumException.GenericError(RuntimeException("An error the server threw!"))
        ): Arrangement = apply {
            coEvery {
                connectionApi.updateConnection(eq(userId), any())
            }.returns(NetworkResponse.Error(exception))
        }

        suspend fun withDeleteConnectionDataAndConversation(conversationId: QualifiedIDEntity): Arrangement = apply {
            coEvery {
                connectionDAO.deleteConnectionDataAndConversation(eq(conversationId))
            }.returns(Unit)
        }

        suspend fun withConnectionEntityByUser(): Arrangement = apply {
            coEvery { connectionDAO.getConnectionByUser(any()) }
                .returns(connectionEntity)
        }

        suspend fun withSuccessfulFetchSelfUserConnectionsResponse(stubUserProfileDTO: UserProfileDTO): Arrangement {
            coEvery { connectionApi.fetchSelfUserConnections(null) }
                .returns(
                    NetworkResponse.Success(
                        stubConnectionResponse,
                        mapOf(),
                        200
                    )
                )
            coEvery {
                userDetailsApi.getUserInfo(any())
            }.returns(NetworkResponse.Success(stubUserProfileDTO, mapOf(), 200))

            withUpdateOrInsertOneOnOneMemberSuccess()

            coEvery {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(flowOf(stubUserEntity))

            coEvery {
                userDAO.upsertUser(any())
            }.returns(Unit)

            return this
        }

        suspend fun withSuccessfulGetUserById(id: QualifiedIDEntity): Arrangement {
            coEvery {
                userDAO.observeUserDetailsByQualifiedID(eq(id))
            }.returns(flowOf(stubUserEntity))

            return this
        }

        suspend fun withGetConnection(connection: ConnectionEntity?): Arrangement = apply {
            coEvery {
                connectionDAO.getConnection(any())
            }.returns(connection)
        }

        suspend fun withGetConnectionByUser(): Arrangement = apply {
            coEvery {
                connectionDAO.getConnectionByUser(any())
            }.returns(connectionEntity)
        }

        fun arrange() = this to connectionRepository
    }
}
