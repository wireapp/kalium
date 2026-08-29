/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class NewConnectionEventHandlerTest {
    @Test
    fun givenNewConnectionEvent_thenConnectionIsPersisted() = runTest {
        val event = newConnectionEvent()
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.connectionRepository.insertConnectionFromEvent(arrangement.transactionContext, event)
        }
    }

    @Test
    fun givenStaleNewConnectionEvent_whenUserDetailsReturnNotFound_thenConnectionIsPersisted() = runTest {
        val event = newConnectionEvent()
        val (arrangement, handler) = Arrangement()
            .withUserFetchResult(Either.Right(ConnectionUserFetchResult.NOT_FOUND))
            .arrange()

        val result = handler.handle(arrangement.transactionContext, event, PENDING_DELIVERY_INFO)

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.connectionRepository.insertConnectionFromEvent(arrangement.transactionContext, event)
        }
    }

    @Test
    fun givenNewConnectionEvent_whenFetchingUserDetailsFails_thenFailureIsPropagated() = runTest {
        val event = newConnectionEvent()
        val failure = CoreFailure.MissingClientRegistration
        val (arrangement, handler) = Arrangement()
            .withUserFetchResult(Either.Left(failure))
            .arrange()

        val result = handler.handle(arrangement.transactionContext, event, PENDING_DELIVERY_INFO)

        assertEquals(Either.Left(failure), result)
        verifySuspend(VerifyMode.not) {
            arrangement.connectionRepository.insertConnectionFromEvent(arrangement.transactionContext, event)
        }
    }

    @Test
    fun givenNewConnectionEventWithStatusPending_thenActiveOneOnOneConversationIsNotResolved() = runTest {
        val event = newConnectionEvent(ConnectionState.PENDING)
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        assertEquals(emptyList(), arrangement.scheduledResolutions)
    }

    @Test
    fun givenNonLiveNewConnectionEventWithStatusAccepted_thenResolveActiveOneOnOneConversationIsScheduled() = runTest {
        val event = newConnectionEvent(ConnectionState.ACCEPTED)
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(arrangement.transactionContext, event, PENDING_DELIVERY_INFO)

        assertEquals(
            listOf(ScheduledResolution(arrangement.transactionContext, event.connection.qualifiedToId, ZERO)),
            arrangement.scheduledResolutions,
        )
    }

    @Test
    fun givenLiveNewConnectionEventWithStatusAccepted_thenResolveActiveOneOnOneConversationIsScheduledWithDelay() = runTest {
        val event = newConnectionEvent(ConnectionState.ACCEPTED)
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        assertEquals(
            listOf(ScheduledResolution(arrangement.transactionContext, event.connection.qualifiedToId, 3.seconds)),
            arrangement.scheduledResolutions,
        )
    }

    @Test
    fun givenNewConnectionEventWithStatusAccepted_whenHandlingEvent_thenCreateUnverifiedWarningMessage() = runTest {
        val event = newConnectionEvent(ConnectionState.ACCEPTED)
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        assertEquals(listOf(event.connection.qualifiedConversationId), arrangement.warningConversationIds)
    }

    @Test
    fun givenNewConnectionEventWithStatusCancelled_whenHandlingEvent_thenDoNotCreateUnverifiedWarningMessage() = runTest {
        val event = newConnectionEvent(ConnectionState.CANCELLED)
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        assertEquals(emptyList(), arrangement.warningConversationIds)
    }

    @Test
    fun givenNewConnectionEvent_whenHandlingEvent_thenHandlePotentialLegalHoldChange() = runTest {
        val event = newConnectionEvent(ConnectionState.CANCELLED)
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        assertEquals(listOf(event), arrangement.legalHoldEvents)
    }

    @Test
    fun givenNewConnectionEventWithStatusAcceptedAndPreviousStatusWasMissingConsent_thenDoNotCreateUnverifiedWarningMessage() = runTest {
        val event = newConnectionEvent(ConnectionState.ACCEPTED)
        val (arrangement, handler) = Arrangement()
            .withConnectionStatus(Either.Right(ConnectionState.MISSING_LEGALHOLD_CONSENT))
            .arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        assertEquals(emptyList(), arrangement.warningConversationIds)
    }

    @Test
    fun givenNewConnectionEventWithStatusAcceptedAndPreviousStatusWasNotMissingConsent_thenCreateUnverifiedWarningMessage() = runTest {
        val event = newConnectionEvent(ConnectionState.ACCEPTED)
        val (arrangement, handler) = Arrangement()
            .withConnectionStatus(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        handler.handle(arrangement.transactionContext, event, LIVE_DELIVERY_INFO)

        assertEquals(listOf(event.connection.qualifiedConversationId), arrangement.warningConversationIds)
    }

    private class Arrangement {
        val transactionContext = mock<CryptoTransactionContext>()
        val userRepository = mock<UserEventRepository>(mode = MockMode.autoUnit)
        val connectionRepository = mock<NewConnectionEventRepository>(mode = MockMode.autoUnit)
        val scheduledResolutions = mutableListOf<ScheduledResolution>()
        val warningConversationIds = mutableListOf<ConversationId>()
        val legalHoldEvents = mutableListOf<Event.User.NewConnection>()
        private var userFetchConfigured = false
        private var connectionStatusConfigured = false

        fun withUserFetchResult(result: Either<CoreFailure, ConnectionUserFetchResult>) = apply {
            userFetchConfigured = true
            everySuspend { userRepository.fetchUserForConnectionEvent(OTHER_USER_ID) } returns result
        }

        fun withConnectionStatus(result: Either<StorageFailure, ConnectionState>) = apply {
            connectionStatusConfigured = true
            everySuspend { connectionRepository.getConnectionStatusForEvent(CONVERSATION_ID) } returns result
        }

        fun arrange(): Pair<Arrangement, NewConnectionEventHandler> {
            if (!userFetchConfigured) {
                withUserFetchResult(Either.Right(ConnectionUserFetchResult.SUCCESS))
            }
            if (!connectionStatusConfigured) {
                withConnectionStatus(Either.Left(StorageFailure.DataNotFound))
            }
            everySuspend {
                connectionRepository.insertConnectionFromEvent(transactionContext, any())
            } returns Either.Right(Unit)

            return this to NewConnectionEventHandlerImpl(
                userRepository = userRepository,
                connectionRepository = connectionRepository,
                scheduleOneOnOneResolution = { context, userId, delay ->
                    scheduledResolutions += ScheduledResolution(context, userId, delay)
                },
                persistUnverifiedWarning = { conversationId ->
                    warningConversationIds += conversationId
                    Either.Right(Unit)
                },
                handleLegalHoldChange = { event ->
                    legalHoldEvents += event
                    Either.Right(Unit)
                },
            )
        }
    }

    private data class ScheduledResolution(
        val transactionContext: CryptoTransactionContext,
        val userId: UserId,
        val delay: Duration,
    )
}
