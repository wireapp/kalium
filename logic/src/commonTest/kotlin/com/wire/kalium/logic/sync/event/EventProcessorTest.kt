package com.wire.kalium.logic.sync.event

import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.ConversationEventReceiver
import com.wire.kalium.logic.sync.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.UserEventReceiver
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EventProcessorTest {

    @Test
    fun givenAEvent_whenSyncing_thenTheLastProcessedEventIdIsUpdated() = runTest(TestKaliumDispatcher.default) {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement().arrange()

        // When
        eventProcessor.processEvent(event)

        // Then
        verify(arrangement.eventRepository)
            .suspendFunction(arrangement.eventRepository::updateLastProcessedEventId)
            .with(eq(event.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationEvent_whenSyncing_thenTheConversationHandlerIsCalled() = runTest(TestKaliumDispatcher.default) {
        // Given
        val event = TestEvent.memberJoin()

        val (arrangement, eventProcessor) = Arrangement().arrange()

        // When
        eventProcessor.processEvent(event)

        // Then
        verify(arrangement.conversationEventReceiver)
            .suspendFunction(arrangement.conversationEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserEvent_whenSyncing_thenTheUserEventHandlerIsCalled() = runTest(TestKaliumDispatcher.default) {
        // Given
        val event = TestEvent.newConnection()

        val (arrangement, eventProcessor) = Arrangement().arrange()

        // When
        eventProcessor.processEvent(event)

        // Then
        verify(arrangement.userEventReceiver)
            .suspendFunction(arrangement.userEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val eventRepository = configure(mock(EventRepository::class)) { stubsUnitByDefault = true }

        @Mock
        val conversationEventReceiver = configure(mock(ConversationEventReceiver::class)) { stubsUnitByDefault = true }

        @Mock
        private val featureConfigEventReceiver: FeatureConfigEventReceiver =
            mock(FeatureConfigEventReceiver::class)

        @Mock
        val userEventReceiver = configure(mock(UserEventReceiver::class)) { stubsUnitByDefault = true }

        val eventProcessor: EventProcessor =
            EventProcessorImpl(eventRepository, conversationEventReceiver, userEventReceiver, featureConfigEventReceiver)

        fun arrange() = this to eventProcessor
    }
}
