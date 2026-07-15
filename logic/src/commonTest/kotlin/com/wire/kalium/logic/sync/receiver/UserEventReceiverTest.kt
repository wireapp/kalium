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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.logoutUseCase.invoke(eq(LogoutReason.REMOVED_CLIENT), eq(true))
        }
    }

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsNotEqualCurrentClient_SoftLogoutNotInvoked() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
        val (arrangement, eventReceiver) = arrange {
            withCurrentClientIdIs(CLIENT_ID2)
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.not) {
            arrangement.logoutUseCase.invoke(any(), any())
        }
    }

    @Test
    fun givenDeleteAccountEvent_SoftLogoutInvoked() = runTest {
        val event = TestEvent.userDelete(userId = SELF_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.logoutUseCase.invoke(eq(LogoutReason.DELETED_ACCOUNT), eq(true))
        }
    }

    @Test
    fun givenUserDeleteEvent_RepoAndPersisMessageAreInvoked() = runTest {
        val event = TestEvent.userDelete(userId = OTHER_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
                result = emptyList()
            )
        }

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userRepository.markUserAsDeletedAndRemoveFromGroupConversations(any()) }
    }

    @Test
    fun givenUserUpdateEvent_RepoIsInvoked() = runTest {
        val event = TestEvent.updateUser(userId = SELF_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withUpdateUserSuccess()
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.updateUserFromEvent(any())
        }
    }

    @Test
    fun givenUserUpdateEvent_whenUserIsNotFoundInLocalDB_thenShouldIgnoreThisEventFailure() = runTest {
        val event = TestEvent.updateUser(userId = OTHER_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withUpdateUserFailure(StorageFailure.DataNotFound)
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenUserUpdateEvent_whenFailsWitOtherError_thenShouldFail() = runTest {
        val event = TestEvent.updateUser(userId = OTHER_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withUpdateUserFailure(StorageFailure.Generic(Throwable("error")))
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Left<StorageFailure.Generic>>(result)
    }

    @Test
    fun givenNewClientEvent_NewClientManagerInvoked() = runTest {
        val event = TestEvent.newClient(clientId = CLIENT_ID1)
        val (arrangement, eventReceiver) = arrange { withCurrentClientIdIs(CLIENT_ID2) }

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.saveNewClientEvent(any())
        }
    }

    @Test
    fun givenNewClientEventIsSameAsCurrent_ThenSkipSavingEvent() = runTest {
        val event = TestEvent.newClient(clientId = CLIENT_ID1)
        val (arrangement, eventReceiver) = arrange { withCurrentClientIdIs(CLIENT_ID1) }

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.saveNewClientEvent(any())
        }
    }

    @Test
    fun givenSessionRefreshSuggestedEvent_thenCurrentSessionIsRefreshed() = runTest {
        val event = TestEvent.sessionRefreshSuggested(EVENT_ID)
        val (arrangement, eventReceiver) = arrange {
            withSessionRefreshSuggestedHandlerResult(Either.Right(Unit))
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRefreshSuggestedEventHandler.handle(eq(event))
        }
    }

    @Test
    fun givenSessionRefreshSuggestedEvent_whenRefreshFails_thenFailureIsPropagated() = runTest {
        val event = TestEvent.sessionRefreshSuggested(EVENT_ID)
        val failure = StorageFailure.Generic(Throwable("refresh failed"))
        val (arrangement, eventReceiver) = arrange {
            withSessionRefreshSuggestedHandlerResult(Either.Left(failure))
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertIs<Either.Left<StorageFailure.Generic>>(result)
    }

    @Test
    fun givenPendingSessionRefreshSuggestedEvent_whenRefreshFails_thenEventIsSkipped() = runTest {
        val event = TestEvent.sessionRefreshSuggested(EVENT_ID)
        val failure = StorageFailure.Generic(Throwable("refresh failed"))
        val (arrangement, eventReceiver) = arrange {
            withSessionRefreshSuggestedHandlerResult(Either.Left(failure))
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.nonLiveDeliveryInfo)

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenNewConnectionEvent_thenConnectionIsPersisted() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING)
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Right(Unit))
            withInsertConnectionFromEventSucceeding()
            withPersistUnverifiedWarningMessageSuccess()
        }

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.connectionRepository.insertConnectionFromEvent(any(), any())
        }
    }

    @Test
    fun givenStaleNewConnectionEvent_whenUserDetailsReturnNotFound_thenConnectionIsPersisted() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING)
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.notFound)
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Left(failure))
            withInsertConnectionFromEventSucceeding()
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.nonLiveDeliveryInfo)

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.connectionRepository.insertConnectionFromEvent(any(), eq(event))
        }
    }

    @Test
    fun givenNewConnectionEvent_whenFetchingUserDetailsFails_thenFailureIsPropagated() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING)
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Left(failure))
        }

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.nonLiveDeliveryInfo)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.connectionRepository.insertConnectionFromEvent(any(), any())
        }
    }

    @Test
    fun givenNewConnectionEventWithStatusPending_thenActiveOneOnOneConversationIsNotResolved() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING).copy()
        val (arrangement, eventReceiver) = arrange {
            withFetchUserInfoReturning(Either.Right(Unit))
            withInsertConnectionFromEventSucceeding()
            withPersistUnverifiedWarningMessageSuccess()
        }

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.not) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any())
        }
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

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.nonLiveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(any(), eq(event.connection.qualifiedToId), eq(ZERO))
        }
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

            eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
            advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(
                    any(),
                    eq(event.connection.qualifiedToId),
                    eq(3.seconds)
                )
            }
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
            eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                    eq(event.connection.qualifiedConversationId),
                    any()
                )
            }
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
            eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
            // then
            verifySuspend(VerifyMode.not) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                    eq(event.connection.qualifiedConversationId),
                    any()
                )
            }
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
            eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.legalHoldHandler.handleNewConnection(eq(event))
            }
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
        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
        // then
        verifySuspend(VerifyMode.not) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
        }
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
        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                eq(event.connection.qualifiedConversationId),
                any()
            )
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val userRepository = mock<UserRepository>()
        val oneOnOneResolver = mock<OneOnOneResolver>()
        val connectionRepository = mock<ConnectionRepository>()
        val logoutUseCase = mock<LogoutUseCase>()
        private val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val clientRepository = mock<ClientRepository>()
        val newGroupConversationSystemMessagesCreator = mock<NewGroupConversationSystemMessagesCreator>()
        val legalHoldRequestHandler = mock<LegalHoldRequestHandler>()
        val legalHoldHandler = mock<LegalHoldHandler>()
        val sessionRefreshSuggestedEventHandler = mock<SessionRefreshSuggestedEventHandler>()

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
            legalHoldHandler,
            sessionRefreshSuggestedEventHandler
        )

        suspend fun withInsertConnectionFromEventSucceeding() = apply {
            everySuspend {
                connectionRepository.insertConnectionFromEvent(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withPersistUnverifiedWarningMessageSuccess() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSaveNewClientSucceeding() = apply {
            everySuspend {
                clientRepository.saveNewClientEvent(any())
            } returns Either.Right(Unit)
        }

        suspend fun withCurrentClientIdIs(clientId: ClientId) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(clientId)
        }

        suspend fun withLogoutUseCaseSucceed() = apply {
            everySuspend {
                logoutUseCase.invoke(any(), any())
            } returns Unit
        }

        suspend fun withHandleLegalHoldNewConnectionSucceeding() = apply {
            everySuspend {
                legalHoldHandler.handleNewConnection(any())
            } returns Either.Right(Unit)
        }

        suspend fun withSessionRefreshSuggestedHandlerResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                sessionRefreshSuggestedEventHandler.handle(any())
            } returns result
        }

        suspend fun withGetConnectionResult(result: Either<StorageFailure, ConversationDetails.Connection>) = apply {
            everySuspend {
                connectionRepository.getConnection(any())
            } returns result
        }

        suspend fun withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(result: List<com.wire.kalium.logic.data.id.ConversationId>) = apply {
            everySuspend {
                userRepository.markUserAsDeletedAndRemoveFromGroupConversations(any())
            } returns Either.Right(result)
        }

        suspend fun withUpdateUserSuccess() = apply {
            everySuspend {
                userRepository.updateUserFromEvent(any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateUserFailure(coreFailure: CoreFailure) = apply {
            everySuspend {
                userRepository.updateUserFromEvent(any())
            } returns Either.Left(coreFailure)
        }

        suspend fun withFetchUserInfoReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                userRepository.fetchUserInfo(any())
            } returns result
        }

        suspend fun withScheduleResolveOneOnOneConversationWithUserId() = apply {
            everySuspend {
                oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(any(), any(), any())
            } returns Job()
        }

        suspend fun arrange() = run {
            withSaveNewClientSucceeding()
            withHandleLegalHoldNewConnectionSucceeding()
            withSessionRefreshSuggestedHandlerResult(Either.Right(Unit))
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
