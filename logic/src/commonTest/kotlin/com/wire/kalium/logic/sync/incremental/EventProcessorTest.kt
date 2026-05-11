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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class EventProcessorTest {

    @Test
    fun givenAEvent_whenSyncing_thenReturnsProcessedEventId() = runTest {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement(this).arrange {}

        // When
        val result = eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())

        // Then
        assertEquals(Either.Right(event.id), result)
    }

    @Test
    fun givenAConversationEvent_whenSyncing_thenTheConversationHandlerIsCalled() = runTest {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement(this)
            .arrange()

        // When
        eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationEventReceiver.onEvent(any(), eq(event), any())
        }
    }

    @Test
    fun givenConversationHandlerFails_whenSyncing_thenReturnsFailure() = runTest {
        // Given
        val event = TestEvent.memberJoin()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withConversationEventReceiverFailingWith(failure)
        }

        // When
        eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())
            // Then
            .shouldFail { assertEquals(failure, it) }
    }

    @Test
    fun givenAUserEvent_whenSyncing_thenTheUserEventHandlerIsCalled() = runTest {
        // Given
        val event = TestEvent.newConnection()

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
        }

        // When
        eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userEventReceiver.onEvent(any(), eq(event), any())
        }
    }

    @Test
    fun givenUserHandlerFails_whenSyncing_thenReturnsFailure() = runTest {
        // Given
        val event = TestEvent.newConnection()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUserEventReceiverFailingWith(failure)
        }

        // When
        eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())
            // Then
            .shouldFail { assertEquals(failure, it) }
    }


    @Test
    fun givenUserPropertiesHandlerFails_whenSyncing_thenReturnsFailure() = runTest {
        // Given
        val event = TestEvent.userPropertyReadReceiptMode()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUserPropertiesEventReceiverFailingWith(failure)
        }

        // When
        eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())
            // Then
            .shouldFail { assertEquals(failure, it) }
    }

    @Test
    fun givenEvent_whenCallerIsCancelled_thenShouldStillProcessNormally() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()

        val callerScope = CoroutineScope(Job())

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUserPropertiesEventReceiverInvoking {
                callerScope.cancel() // Cancel during event processing
                Either.Right(Unit)
            }
        }

        callerScope.launch {
            eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())
        }.join()
        advanceUntilIdle()
        assertFalse(callerScope.isActive)
        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertiesEventReceiver.onEvent(any(), any(), any())
        }
    }

    @Test
    fun givenEvent_whenProcessingScopeIsCancelledMidwayThrough_thenShouldProceedAnywayAndCancellationIsPropagated() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()

        val processingScope = CoroutineScope(Job())

        val (arrangement, eventProcessor) = Arrangement(processingScope).arrange {
            withUserPropertiesEventReceiverInvoking {
                processingScope.cancel() // Cancel during event processing
                Either.Right(Unit)
            }
        }

        assertFailsWith(CancellationException::class) {
            eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())
            advanceUntilIdle()
        }
        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertiesEventReceiver.onEvent(any(), any(), any())
        }
    }

    @Test
    fun givenEvent_whenProcessingScopeIsAlreadyCancelled_thenShouldNotProcessAndPropagateCancellation() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()

        val processingScope = CoroutineScope(Job())
        processingScope.cancel()

        val (arrangement, eventProcessor) = Arrangement(processingScope).arrange {}

        assertFailsWith(CancellationException::class) {
            eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())
            advanceUntilIdle()
        }
        // Then
        verifySuspend(VerifyMode.not) {
            arrangement.userPropertiesEventReceiver.onEvent(any(), any(), any())
        }
    }

    @Test
    fun givenDisableEventProcessingEnabled_whenProcessing_thenReturnsNullAndDoesNotDispatch() = runTest {
        val event = TestEvent.memberJoin()
        val (arrangement, eventProcessor) = Arrangement(this).arrange()
        eventProcessor.disableEventProcessing = true

        val result = eventProcessor.processEvent(arrangement.transactionContext, event.wrapInEnvelope())

        assertEquals(Either.Right(null), result)
        verifySuspend(VerifyMode.not) { arrangement.conversationEventReceiver.onEvent(any(), any(), any()) }
    }

    private class Arrangement(
        val processingScope: CoroutineScope
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val conversationEventReceiver = mock<ConversationEventReceiver>()
        val userEventReceiver = mock<UserEventReceiver>()
        val teamEventReceiver = mock<TeamEventReceiver>()
        val featureConfigEventReceiver = mock<FeatureConfigEventReceiver>(mode = MockMode.autoUnit)
        val userPropertiesEventReceiver = mock<UserPropertiesEventReceiver>()
        val federationEventReceiver = mock<FederationEventReceiver>(mode = MockMode.autoUnit)

        suspend fun withConversationEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                conversationEventReceiver.onEvent(any(), any(), any())
            } returns result
        }

        suspend fun withConversationEventReceiverSucceeding() = withConversationEventReceiverReturning(Either.Right(Unit))

        suspend fun withConversationEventReceiverFailingWith(failure: CoreFailure) = withConversationEventReceiverReturning(
            Either.Left(failure)
        )

        suspend fun withUserEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                userEventReceiver.onEvent(any(), any(), any())
            } returns result
        }

        suspend fun withUserEventReceiverSucceeding() = withUserEventReceiverReturning(Either.Right(Unit))

        suspend fun withUserEventReceiverFailingWith(failure: CoreFailure) = withUserEventReceiverReturning(
            Either.Left(failure)
        )

        suspend fun withTeamEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                teamEventReceiver.onEvent(any(), any(), any())
            } returns result
        }

        suspend fun withTeamEventReceiverSucceeding() = withTeamEventReceiverReturning(Either.Right(Unit))

        suspend fun withUserPropertiesEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                userPropertiesEventReceiver.onEvent(any(), any(), any())
            } returns result
        }

        suspend fun withUserPropertiesEventReceiverInvoking(invocation: () -> Either<CoreFailure, Unit>) = apply {
            everySuspend {
                userPropertiesEventReceiver.onEvent(any(), any(), any())
            } calls { invocation() }
        }

        suspend fun withUserPropertiesEventReceiverSucceeding() = withUserPropertiesEventReceiverReturning(Either.Right(Unit))

        suspend fun withUserPropertiesEventReceiverFailingWith(failure: CoreFailure) = withUserPropertiesEventReceiverReturning(
            Either.Left(failure)
        )

        suspend fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            withConversationEventReceiverSucceeding()
            withUserEventReceiverSucceeding()
            withTeamEventReceiverSucceeding()
            withUserPropertiesEventReceiverSucceeding()
            block()
            this to EventProcessorImpl(
                conversationEventReceiver,
                userEventReceiver,
                teamEventReceiver,
                featureConfigEventReceiver,
                userPropertiesEventReceiver,
                federationEventReceiver,
                processingScope
            )
        }
    }
}
