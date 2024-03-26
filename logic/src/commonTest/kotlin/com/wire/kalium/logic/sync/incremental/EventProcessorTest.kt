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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.functional.Either
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
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventProcessorTest {

    @Test
    fun givenAEvent_whenSyncing_thenTheLastProcessedEventIdIsUpdated() = runTest {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement()
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())

        // Then
        verify(arrangement.eventRepository)
            .suspendFunction(arrangement.eventRepository::updateLastProcessedEventId)
            .with(eq(event.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationEvent_whenSyncing_thenTheConversationHandlerIsCalled() = runTest {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement()
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())

        // Then
        verify(arrangement.conversationEventReceiver)
            .suspendFunction(arrangement.conversationEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationHandlerFails_whenSyncing_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.memberJoin()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement()
            .withConversationEventReceiverFailingWith(failure)
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())
            .shouldFail { assertEquals(failure, it) }

        // Then
        verify(arrangement.eventRepository)
            .suspendFunction(arrangement.eventRepository::updateLastProcessedEventId)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenAUserEvent_whenSyncing_thenTheUserEventHandlerIsCalled() = runTest {
        // Given
        val event = TestEvent.newConnection()

        val (arrangement, eventProcessor) = Arrangement()
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())

        // Then
        verify(arrangement.userEventReceiver)
            .suspendFunction(arrangement.userEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserHandlerFails_whenSyncing_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.newConnection()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement()
            .withUserEventReceiverFailingWith(failure)
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())
            .shouldFail { assertEquals(failure, it) }

        // Then
        verify(arrangement.eventRepository)
            .suspendFunction(arrangement.eventRepository::updateLastProcessedEventId)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenNonTransientEvent_whenProcessingEvent_thenLastProcessedEventIdIsUpdated() = runTest {
        // Given
        val envelope = TestEvent.newConnection().wrapInEnvelope(isTransient = false)

        val (arrangement, eventProcessor) = Arrangement()
            .withUpdateLastProcessedEventId(envelope.event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(envelope.event.wrapInEnvelope())

        // Then
        verify(arrangement.eventRepository)
            .suspendFunction(arrangement.eventRepository::updateLastProcessedEventId)
            .with(eq(envelope.event.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenTransientEvent_whenProcessingEvent_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.newConnection().wrapInEnvelope(isTransient = true)

        val (arrangement, eventProcessor) = Arrangement()
            .arrange()

        // When
        eventProcessor.processEvent(event)

        // Then
        verify(arrangement.eventRepository)
            .suspendFunction(arrangement.eventRepository::updateLastProcessedEventId)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserPropertyEvent_whenProcessingEvent_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.userPropertyReadReceiptMode()

        val (arrangement, eventProcessor) = Arrangement()
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        eventProcessor.processEvent(event.wrapInEnvelope())

        verify(arrangement.userPropertiesEventReceiver)
            .suspendFunction(arrangement.userPropertiesEventReceiver::onEvent)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserPropertiesHandlerFails_whenSyncing_thenLastProcessedEventIdIsNotUpdated() = runTest {
        // Given
        val event = TestEvent.userPropertyReadReceiptMode()
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, eventProcessor) = Arrangement()
            .withUserPropertiesEventReceiverFailingWith(failure)
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event.wrapInEnvelope())
            .shouldFail { assertEquals(failure, it) }

        // Then
        verify(arrangement.eventRepository)
            .suspendFunction(arrangement.eventRepository::updateLastProcessedEventId)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement : FeatureConfigEventReceiverArrangement by FeatureConfigEventReceiverArrangementImpl() {

        @Mock
        val eventRepository = configure(mock(EventRepository::class)) { stubsUnitByDefault = true }

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
            withConversationEventReceiverSucceeding()
            withUserEventReceiverSucceeding()
            withTeamEventReceiverSucceeding()
            withUserPropertiesEventReceiverSucceeding()
        }

        fun withUpdateLastProcessedEventId(eventId: String, result: Either<StorageFailure, Unit>) = apply {
            given(eventRepository)
                .suspendFunction(eventRepository::updateLastProcessedEventId)
                .whenInvokedWith(eq(eventId))
                .then { result }
        }

        fun withConversationEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            given(conversationEventReceiver)
                .suspendFunction(conversationEventReceiver::onEvent)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withConversationEventReceiverSucceeding() = withConversationEventReceiverReturning(Either.Right(Unit))

        fun withConversationEventReceiverFailingWith(failure: CoreFailure) = withConversationEventReceiverReturning(
            Either.Left(failure)
        )

        fun withUserEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            given(userEventReceiver)
                .suspendFunction(userEventReceiver::onEvent)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUserEventReceiverSucceeding() = withUserEventReceiverReturning(Either.Right(Unit))

        fun withUserEventReceiverFailingWith(failure: CoreFailure) = withUserEventReceiverReturning(
            Either.Left(failure)
        )

        fun withTeamEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            given(teamEventReceiver)
                .suspendFunction(teamEventReceiver::onEvent)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withTeamEventReceiverSucceeding() = withTeamEventReceiverReturning(Either.Right(Unit))

        fun withUserPropertiesEventReceiverReturning(result: Either<CoreFailure, Unit>) = apply {
            given(userPropertiesEventReceiver)
                .suspendFunction(userPropertiesEventReceiver::onEvent)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUserPropertiesEventReceiverSucceeding() = withUserPropertiesEventReceiverReturning(Either.Right(Unit))

        fun withUserPropertiesEventReceiverFailingWith(failure: CoreFailure) = withUserPropertiesEventReceiverReturning(
            Either.Left(failure)
        )

        fun arrange(block: Arrangement.() -> Unit = {}) = apply(block).let {
            this to EventProcessorImpl(
                eventRepository,
                conversationEventReceiver,
                userEventReceiver,
                teamEventReceiver,
                featureConfigEventReceiver,
                userPropertiesEventReceiver,
                federationEventReceiver
            )
        }
    }
}
