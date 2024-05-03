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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class UserEventReceiverTest {

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsEqualCurrentClient_SoftLogoutInvoked() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
        val (arrangement, eventReceiver) = arrange {
            withCurrentClientIdIs(CLIENT_ID1)
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.logoutUseCase.invoke(eq(LogoutReason.REMOVED_CLIENT), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsNotEqualCurrentClient_SoftLogoutNotInvoked() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
        val (arrangement, eventReceiver) = arrange {
            withCurrentClientIdIs(CLIENT_ID2)
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.logoutUseCase.invoke(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenDeleteAccountEvent_SoftLogoutInvoked() = runTest {
        val event = TestEvent.userDelete(userId = SELF_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.logoutUseCase.invoke(eq(LogoutReason.DELETED_ACCOUNT), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserDeleteEvent_RepoAndPersisMessageAreInvoked() = runTest {
        val event = TestEvent.userDelete(userId = OTHER_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
                result = emptyList(),
                userIdMatcher = AnyMatcher(valueOf())
            )
        }

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        coVerify { arrangement.userRepository.markUserAsDeletedAndRemoveFromGroupConversations(any()) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserUpdateEvent_RepoIsInvoked() = runTest {
        val event = TestEvent.updateUser(userId = SELF_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withUpdateUserSuccess()
        }

        val result = eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Right<Unit>>(result)
        coVerify {
            arrangement.userRepository.updateUserFromEvent(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserUpdateEvent_whenUserIsNotFoundInLocalDB_thenShouldIgnoreThisEventFailure() = runTest {
        val event = TestEvent.updateUser(userId = OTHER_USER_ID)
        val (_, eventReceiver) = arrange {
            withUpdateUserFailure(StorageFailure.DataNotFound)
        }

        val result = eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenUserUpdateEvent_whenFailsWitOtherError_thenShouldFail() = runTest {
        val event = TestEvent.updateUser(userId = OTHER_USER_ID)
        val (_, eventReceiver) = arrange {
            withUpdateUserFailure(StorageFailure.Generic(Throwable("error")))
        }

        val result = eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Left<StorageFailure.Generic>>(result)
    }

    @Test
    fun givenNewClientEvent_NewClientManagerInvoked() = runTest {
        val event = TestEvent.newClient()
        val (arrangement, eventReceiver) = arrange { }

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.clientRepository.saveNewClientEvent(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConnectionEvent_thenConnectionIsPersisted() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING)
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Right(Unit))
            withInsertConnectionFromEventSucceeding()
            withPersistUnverifiedWarningMessageSuccess()
        }

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.connectionRepository.insertConnectionFromEvent(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConnectionEventWithStatusPending_thenActiveOneOnOneConversationIsNotResolved() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING).copy()
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Right(Unit))
            withInsertConnectionFromEventSucceeding()
            withPersistUnverifiedWarningMessageSuccess()
        }

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNonLiveNewConnectionEventWithStatusAccepted_thenResolveActiveOneOnOneConversationIsScheduled() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.ACCEPTED)
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Right(Unit))
            withInsertConnectionFromEventSucceeding()
            withScheduleResolveOneOnOneConversationWithUserId()
            withPersistUnverifiedWarningMessageSuccess()
        }

        eventReceiver.onEvent(event, TestEvent.nonLiveDeliveryInfo)

        coVerify {
            arrangement.oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(eq(event.connection.qualifiedToId), eq(ZERO))
        }.wasInvoked(exactly = once)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLiveNewConnectionEventWithStatusAccepted_thenResolveActiveOneOnOneConversationIsScheduledWithDelay() =
        runTest(TestKaliumDispatcher.default) {
            val event = TestEvent.newConnection(status = ConnectionState.ACCEPTED)
            val (arrangement, eventReceiver) = arrange {
                withFetchUserInfoReturning(Either.Right(Unit))
                withInsertConnectionFromEventSucceeding()
                withScheduleResolveOneOnOneConversationWithUserId()
                withPersistUnverifiedWarningMessageSuccess()
            }

            eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)
            advanceUntilIdle()

            coVerify {
                arrangement.oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(
                    eq(event.connection.qualifiedToId),
                    eq(3.seconds)
                )
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenNewConnectionEventWithStatusAccepted_whenHandlingEvent_thenCreateUnverifiedWarningMessage() =
        runTest(TestKaliumDispatcher.default) {
            // given
            val event = TestEvent.newConnection(status = ConnectionState.ACCEPTED)
            val (arrangement, eventReceiver) = arrange {
                withFetchUserInfoReturning(Either.Right(Unit))
                withInsertConnectionFromEventSucceeding()
                withScheduleResolveOneOnOneConversationWithUserId()
                withPersistUnverifiedWarningMessageSuccess()
            }
            // when
            eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)
            // then
            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(event.connection.qualifiedConversationId))
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenNewConnectionEventWithStatusCancelled_whenHandlingEvent_thenDoNotCreateUnverifiedWarningMessage() =
        runTest(TestKaliumDispatcher.default) {
            // given
            val event = TestEvent.newConnection(status = ConnectionState.CANCELLED)
            val (arrangement, eventReceiver) = arrange {
                withFetchUserInfoReturning(Either.Right(Unit))
                withInsertConnectionFromEventSucceeding()
                withScheduleResolveOneOnOneConversationWithUserId()
                withPersistUnverifiedWarningMessageSuccess()
            }
            // when
            eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)
            // then
            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(event.connection.qualifiedConversationId))
            }.wasNotInvoked()
        }

    @Test
    fun givenNewConnectionEvent_whenHandlingEvent_thenHandlePotentialLegalHoldChange() =
        runTest(TestKaliumDispatcher.default) {
            // given
            val event = TestEvent.newConnection(status = ConnectionState.CANCELLED)
            val (arrangement, eventReceiver) = arrange {
                withFetchUserInfoReturning(Either.Right(Unit))
                withInsertConnectionFromEventSucceeding()
                withScheduleResolveOneOnOneConversationWithUserId()
                withPersistUnverifiedWarningMessageSuccess()
            }
            // when
            eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)
            // then
            coVerify {
                arrangement.legalHoldHandler.handleNewConnection(eq(event))
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenNewConnectionEventWithStatusAcceptedAndPreviousStatusWasMissingConsent_thenDoNotCreateUnverifiedWarningMessage() = runTest {
        // given
        val event = TestEvent.newConnection(status = ConnectionState.ACCEPTED).copy()
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Right(Unit))
            withInsertConnectionFromEventSucceeding()
            withScheduleResolveOneOnOneConversationWithUserId()
            withPersistUnverifiedWarningMessageSuccess()
            withGetConnectionResult(
                Either.Right(
                    TestConversationDetails.CONNECTION.copy(
                        conversationId = event.connection.qualifiedConversationId,
                        connection = TestConnection.CONNECTION.copy(status = ConnectionState.MISSING_LEGALHOLD_CONSENT)
                    )
                )
            )
        }
        // when
        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)
        // then
        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNewConnectionEventWithStatusAcceptedAndPreviousStatusWasNotMissingConsent_thenCreateUnverifiedWarningMessage() = runTest {
        // given
        val event = TestEvent.newConnection(status = ConnectionState.ACCEPTED).copy()
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Right(Unit))
            withInsertConnectionFromEventSucceeding()
            withScheduleResolveOneOnOneConversationWithUserId()
            withPersistUnverifiedWarningMessageSuccess()
            withGetConnectionResult(Either.Left(StorageFailure.DataNotFound))
        }
        // when
        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)
        // then
        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                eq(event.connection.qualifiedConversationId)
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl() {
        @Mock
        val connectionRepository = mock(ConnectionRepository::class)

        @Mock
        val logoutUseCase = mock(LogoutUseCase::class)

        @Mock
        private val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val newGroupConversationSystemMessagesCreator = mock(NewGroupConversationSystemMessagesCreator::class)

        @Mock
        val legalHoldRequestHandler = mock(LegalHoldRequestHandler::class)

        @Mock
        val legalHoldHandler = mock(LegalHoldHandler::class)

        private val userEventReceiver: UserEventReceiver = UserEventReceiverImpl(
            clientRepository,
            connectionRepository,
            userRepository,
            logoutUseCase,
            oneOnOneResolver,
            SELF_USER_ID,
            currentClientIdProvider,
            lazy { newGroupConversationSystemMessagesCreator },
            legalHoldRequestHandler,
            legalHoldHandler
        )

        suspend fun withInsertConnectionFromEventSucceeding() = apply {
            coEvery {
                connectionRepository.insertConnectionFromEvent(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withPersistUnverifiedWarningMessageSuccess() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSaveNewClientSucceeding() = apply {
            coEvery {
                clientRepository.saveNewClientEvent(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withCurrentClientIdIs(clientId: ClientId) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        suspend fun withLogoutUseCaseSucceed() = apply {
            coEvery {
                logoutUseCase.invoke(any(), any())
            }.returns(Unit)
        }

        suspend fun withHandleLegalHoldNewConnectionSucceeding() = apply {
            coEvery {
                legalHoldHandler.handleNewConnection(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetConnectionResult(result: Either<StorageFailure, ConversationDetails.Connection>) = apply {
            coEvery {
                connectionRepository.getConnection(any())
            }.returns(result)
        }

        suspend fun arrange() = run {
            withSaveNewClientSucceeding()
            withHandleLegalHoldNewConnectionSucceeding()
            withGetConnectionResult(Either.Left(StorageFailure.DataNotFound))
            block()
            this@Arrangement to userEventReceiver
        }
    }

    companion object {
        private suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        const val EVENT_ID = "1234"
        val SELF_USER_ID = UserId("alice", "wonderland")
        val OTHER_USER_ID = UserId("john", "public")
        val CLIENT_ID1 = ClientId("clientId1")
        val CLIENT_ID2 = ClientId("clientId2")

    }
}
