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
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.authenticated.connection.ConnectionResponse
import com.wire.kalium.network.api.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.ErrorResponse
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
import com.wire.kalium.util.ConversationPersistenceApi
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.EqualsMatcher
import io.mockative.matchers.Matcher
import io.mockative.matchers.PredicateMatcher
import io.mockative.matches
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

@OptIn(ConversationPersistenceApi::class)
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
        val result = connectionRepository.fetchSelfUserConnections(arrangement.transactionContext)

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
        val result = connectionRepository.fetchSelfUserConnections(arrangement.transactionContext)

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
            .withSuccessfulCreateConnectionResponse(
                userId = EqualsMatcher(userId)
            )
            .withSelfUserTeamId(Either.Right(TestUser.SELF.teamId))
            .withFetchConversationSucceed()
            .withPersistConversationsSucceed()

        // when
        val result = connectionRepository.sendUserConnection(arrangement.transactionContext, UserId(userId.value, userId.domain))

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
            .withFetchConversationSucceed()
            .withPersistConversationsSucceed()

        // when
        val result = connectionRepository.sendUserConnection(arrangement.transactionContext, UserId(userId.value, userId.domain))

        // then
        result.shouldFail()
        coVerify {
            arrangement.connectionApi.createConnection(eq(userId))
        }.wasInvoked(once)
        coVerify {
            arrangement.memberDAO.updateOrInsertOneOnOneMember(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.conversationRepository.fetchConversation(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenAConnectionRequest_WhenSendingAConnectionAndPersistingReturnsAnError_thenTheConnectionShouldNotBePersisted() = runTest {
        // given
        val userId = NetworkUserId("user_id", "domain_id")
        val expectedConnection = Arrangement.stubConnectionOne.copy(status = ConnectionStateDTO.SENT)
        val (arrangement, connectionRepository) = Arrangement().arrange()
        arrangement
            .withSuccessfulFetchSelfUserConnectionsResponse(arrangement.stubUserProfileDTO)
            .withSuccessfulGetUserById(arrangement.stubUserEntity.id)
            .withSuccessfulCreateConnectionResponse(
                result = expectedConnection,
                userId = EqualsMatcher(userId)
            )
            .withSuccessfulGetConversationById(arrangement.stubConversationID1)
            .withErrorOnPersistingConnectionResponse(userId)
            .withSelfUserTeamId(Either.Right(TestUser.SELF.teamId))
            .withFetchConversationSucceed()
            .withPersistConversationsSucceed()

        // when
        val result = connectionRepository.sendUserConnection(arrangement.transactionContext, UserId(userId.value, userId.domain))

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
        val result = connectionRepository.updateConnectionStatus(arrangement.transactionContext, UserId(userId.value, userId.domain), ConnectionState.ACCEPTED)
        result.shouldSucceed { Arrangement.stubConnectionOne }

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
        val result = connectionRepository.updateConnectionStatus(arrangement.transactionContext, UserId(userId.value, userId.domain), ConnectionState.NOT_CONNECTED)

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
        val result = connectionRepository.updateConnectionStatus(arrangement.transactionContext, UserId(userId.value, userId.domain), ConnectionState.ACCEPTED)

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
        val result = connectionRepository.updateConnectionStatus(arrangement.transactionContext, UserId(userId.value, userId.domain), ConnectionState.PENDING)

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
        val result = connectionRepository.ignoreConnectionRequest(arrangement.transactionContext, UserId(userId.value, userId.domain))
        result.shouldSucceed { Arrangement.stubConnectionOne }

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
        val result = connectionRepository.ignoreConnectionRequest(arrangement.transactionContext, UserId(userId.value, userId.domain))
        result.shouldSucceed { Arrangement.stubConnectionOne }

        // then
        coVerify {
            arrangement.connectionDAO.insertConnection(
                arrangement.stubConnectionEntity.copy(
                    lastUpdateDate = any(),
                    status = ConnectionEntity.State.IGNORED
                )
            )
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
        val result = connectionRepository.ignoreConnectionRequest(arrangement.transactionContext, UserId(userId.value, userId.domain))
        result.shouldSucceed { Arrangement.stubConnectionOne }

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
        val result = connectionRepository.ignoreConnectionRequest(arrangement.transactionContext, UserId(userId.value, userId.domain))
        result.shouldSucceed { Arrangement.stubConnectionOne }

        // then
        coVerify {
            arrangement.connectionDAO.insertConnection(
                arrangement.stubConnectionEntity.copy(
                    lastUpdateDate = any(),
                    status = ConnectionEntity.State.IGNORED
                )
            )
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
            val result = connectionRepository.ignoreConnectionRequest(arrangement.transactionContext, UserId(userId.value, userId.domain))
            result.shouldFail { Arrangement.stubConnectionOne }

            // then
            coVerify {
                arrangement.connectionDAO.insertConnection(
                    arrangement.stubConnectionEntity.copy(
                        status = ConnectionEntity.State.IGNORED
                    )
                )
            }.wasInvoked(exactly = 0)
        }

    @Test
    fun givenBadConnectionRequestError_whenUpdatingRemote_thenRecoverByGettingTheCorrectStatus() = runTest {
        // given
        val userId = UserId("user_id", "domain_id")
        val expectedRecoveryResponse = Arrangement.stubConnectionOne.copy(
            qualifiedToId = userId.toApi(),
            status = ConnectionStateDTO.BLOCKED
        )
        val (arrangement, connectionRepository) = Arrangement()
            .withErrorUpdatingConnectionStatusResponse(
                userId = userId.toApi(),
                exception = KaliumException.InvalidRequestError(
                    ErrorResponse(
                        message = "bad connection update",
                        code = 403,
                        label = "bad-conn-update"
                    )
                )
            ).withUserConnectionInfo(
                result = NetworkResponse.Success(
                    expectedRecoveryResponse,
                    emptyMap(),
                    200
                ),
                userId = EqualsMatcher(userId.toApi())
            )
            .arrange()

        connectionRepository.updateRemoteConnectionStatus(arrangement.transactionContext, userId, ConnectionState.ACCEPTED)

        coVerify {
            arrangement.connectionDAO.insertConnection(ConnectionMapperImpl().fromApiToDao(expectedRecoveryResponse))
        }.wasInvoked(exactly = 1)
    }

    private class Arrangement :
        MemberDAOArrangement by MemberDAOArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val conversationDAO = mock(ConversationDAO::class)
        val conversationRepository = mock(ConversationRepository::class)
        val connectionDAO = mock(ConnectionDAO::class)
        val connectionApi = mock(ConnectionApi::class)
        val userDetailsApi = mock(UserDetailsApi::class)
        val userDAO = mock(UserDAO::class)
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)
        val persistConversations = mock(PersistConversationsUseCase::class)

        val connectionRepository = ConnectionDataSource(
            conversationDAO = conversationDAO,
            connectionApi = connectionApi,
            connectionDAO = connectionDAO,
            userDAO = userDAO,
            memberDAO = memberDAO,
            conversationRepository = conversationRepository,
            persistConversations = persistConversations
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

        suspend fun withFetchConversationSucceed(): Arrangement {
            coEvery {
                conversationRepository.fetchConversation(any())
            }.returns(Either.Right(TestConversation.CONVERSATION_RESPONSE))
            return this
        }

        suspend fun withPersistConversationsSucceed(): Arrangement {
            coEvery {
                persistConversations(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
            return this
        }

        suspend fun withSuccessfulCreateConnectionResponse(
            result: ConnectionDTO = stubConnectionOne,
            userId: Matcher<NetworkUserId>
        ): Arrangement {
            coEvery {
                connectionApi.createConnection(matches { userId.matches(it) })
            }.returns(NetworkResponse.Success(result, mapOf(), 200))

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

        suspend fun withUserConnectionInfo(
            result: NetworkResponse<ConnectionDTO>,
            userId: Matcher<com.wire.kalium.network.api.model.UserId>
        ) = apply {
            coEvery {
                connectionApi.userConnectionInfo(
                    matches { userId.matches(it) }
                )
            }.returns(result)
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

        companion object {
            val stubConnectionOne = ConnectionDTO(
                conversationId = "conversationId1",
                from = "fromId",
                lastUpdate = Instant.UNIX_FIRST_DATE,
                qualifiedConversationId = ConversationId("conversationId1", "domain"),
                qualifiedToId = NetworkUserId("connectionId1", "domain"),
                status = ConnectionStateDTO.ACCEPTED,
                toId = "connectionId1"
            )
        }
    }
}
