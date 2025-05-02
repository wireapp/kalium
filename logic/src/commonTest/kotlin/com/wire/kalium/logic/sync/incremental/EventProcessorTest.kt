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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.util.arrangement.eventHandler.FeatureConfigEventReceiverArrangement
import com.wire.kalium.logic.util.arrangement.eventHandler.FeatureConfigEventReceiverArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    fun givenAEvent_whenSyncing_thenTheLastProcessedEventIdIsUpdated() = runTest {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
        }

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())

        // Then
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(eq(event.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationEvent_whenSyncing_thenTheConversationHandlerIsCalled() = runTest {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement(this)
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())

        // Then
        coVerify {
            arrangement.conversationEventReceiver.onEvent(eq(event), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationHandlerFails_whenSyncing_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.memberJoin()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withConversationEventReceiverFailingWith(failure)
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
        }

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())
            .shouldFail { assertEquals(failure, it) }

        // Then
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenAUserEvent_whenSyncing_thenTheUserEventHandlerIsCalled() = runTest {
        // Given
        val event = TestEvent.newConnection()

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
        }

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())

        // Then
        coVerify {
            arrangement.userEventReceiver.onEvent(eq(event), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserHandlerFails_whenSyncing_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.newConnection()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUserEventReceiverFailingWith(failure)
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
        }

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())
            .shouldFail { assertEquals(failure, it) }

        // Then
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNonTransientEvent_whenProcessingEvent_thenLastProcessedEventIdIsUpdated() = runTest {
        // Given
        val envelope = TestEvent.newConnection().wrapInEnvelope(isTransient = false)

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUpdateLastProcessedEventId(envelope.event.id, Either.Right(Unit))
        }

        // When
        eventProcessor.processEvent(envelope.event.wrapInEnvelope())

        // Then
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(eq(envelope.event.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenTransientEvent_whenProcessingEvent_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.newConnection().wrapInEnvelope(isTransient = true)

        val (arrangement, eventProcessor) = Arrangement(this).arrange()

        // When
        eventProcessor.processEvent(event)

        // Then
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUserPropertyEvent_whenProcessingEvent_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.userPropertyReadReceiptMode()

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
        }

        eventProcessor.processEvent(event.wrapInEnvelope())

        coVerify {
            arrangement.userPropertiesEventReceiver.onEvent(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserPropertiesHandlerFails_whenSyncing_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.userPropertyReadReceiptMode()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUserPropertiesEventReceiverFailingWith(failure)
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
        }

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())
            .shouldFail { assertEquals(failure, it) }

        // Then
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenEvent_whenCallerIsCancelled_thenShouldStillProcessNormally() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()

        val callerScope = CoroutineScope(Job())

        val (arrangement, eventProcessor) = Arrangement(this).arrange {
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            withUserPropertiesEventReceiverInvoking {
                callerScope.cancel() // Cancel during event processing
                Either.Right(Unit)
            }
        }

        callerScope.launch {
            eventProcessor.processEvent(event.wrapInEnvelope())
        }.join()
        advanceUntilIdle()
        assertFalse(callerScope.isActive)
        // Then
        coVerify {
            arrangement.userPropertiesEventReceiver.onEvent(any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEvent_whenProcessingScopeIsCancelledMidwayThrough_thenShouldProceedAnywayAndCancellationIsPropagated() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()

        val processingScope = CoroutineScope(Job())

        val (arrangement, eventProcessor) = Arrangement(processingScope).arrange {
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            withUserPropertiesEventReceiverInvoking {
                processingScope.cancel() // Cancel during event processing
                Either.Right(Unit)
            }
        }

        assertFailsWith(CancellationException::class) {
            eventProcessor.processEvent(event.wrapInEnvelope())
            advanceUntilIdle()
        }
        // Then
        coVerify {
            arrangement.userPropertiesEventReceiver.onEvent(any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEvent_whenProcessingScopeIsAlreadyCancelled_thenShouldNotProcessAndPropagateCancellation() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()

        val processingScope = CoroutineScope(Job())
        processingScope.cancel()

        val (arrangement, eventProcessor) = Arrangement(processingScope).arrange {
            withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
        }

        assertFailsWith(CancellationException::class) {
            eventProcessor.processEvent(event.wrapInEnvelope())
            advanceUntilIdle()
        }
        // Then
        coVerify {
            arrangement.userPropertiesEventReceiver.onEvent(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenAnEvent_whenProcessingEvent_thenAcknowledgeIsCalled() = runTest {
        // Given
        val event = TestEvent.newConnection().wrapInEnvelope(isTransient = false)

        val (arrangement, eventProcessor) = Arrangement(this).arrange() {
            withUpdateLastProcessedEventId(event.event.id, Either.Right(Unit))
        }

        // When
        eventProcessor.processEvent(event)

        // Then
        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.eventRepository.acknowledgeEvent(eq(event))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement(
        val processingScope: CoroutineScope
    ) : FeatureConfigEventReceiverArrangement by FeatureConfigEventReceiverArrangementImpl() {

        @Mock
        val eventRepository = mock(EventRepository::class)

        @Mock
        val conversationEventReceiver = mock(ConversationEventReceiver::class)

        @Mock
        val userEventReceiver = mock(UserEventReceiver::class)

        @Mock
        val teamEventReceiver = mock(TeamEventReceiver::class)

        @Mock
        val userPropertiesEventReceiver = mock(UserPropertiesEventReceiver::class)

        @Mock
        val federationEventReceiver = mock(FederationEventReceiver::class)

        init {
            runBlocking {
                withAcknowledgeEventReceiverReturning(Unit.right())
            }
        }

        suspend fun withUpdateLastProcessedEventId(eventId: String, result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                eventRepository.updateLastProcessedEventId(eq(eventId))
            }.returns(result)
        }

        suspend fun withConversationEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationEventReceiver.onEvent(any(), any())
            }.returns(result)
        }

        suspend fun withConversationEventReceiverSucceeding() = withConversationEventReceiverReturning(Either.Right(Unit))

        suspend fun withConversationEventReceiverFailingWith(failure: CoreFailure) = withConversationEventReceiverReturning(
            Either.Left(failure)
        )

        suspend fun withUserEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                userEventReceiver.onEvent(any(), any())
            }.returns(result)
        }

        suspend fun withUserEventReceiverSucceeding() = withUserEventReceiverReturning(Either.Right(Unit))

        suspend fun withUserEventReceiverFailingWith(failure: CoreFailure) = withUserEventReceiverReturning(
            Either.Left(failure)
        )

        suspend fun withTeamEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                teamEventReceiver.onEvent(any(), any())
            }.returns(result)
        }

        suspend fun withTeamEventReceiverSucceeding() = withTeamEventReceiverReturning(Either.Right(Unit))

        suspend fun withUserPropertiesEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                userPropertiesEventReceiver.onEvent(any(), any())
            }.returns(result)
        }

        suspend fun withUserPropertiesEventReceiverInvoking(invocation: () -> Either<CoreFailure, Unit>) = apply {
            coEvery {
                userPropertiesEventReceiver.onEvent(any(), any())
            }.invokes(invocation)
        }

        suspend fun withUserPropertiesEventReceiverSucceeding() = withUserPropertiesEventReceiverReturning(Either.Right(Unit))

        suspend fun withUserPropertiesEventReceiverFailingWith(failure: CoreFailure) = withUserPropertiesEventReceiverReturning(
            Either.Left(failure)
        )

        suspend fun withAcknowledgeEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                eventRepository.acknowledgeEvent(any())
            }.returns(result)
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            withConversationEventReceiverSucceeding()
            withUserEventReceiverSucceeding()
            withTeamEventReceiverSucceeding()
            withUserPropertiesEventReceiverSucceeding()
            block()
            this to EventProcessorImpl(
                eventRepository,
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
