package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
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

        val (arrangement, eventProcessor) = Arrangement()
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

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

        val (arrangement, eventProcessor) = Arrangement()
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

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

        val (arrangement, eventProcessor) = Arrangement()
//             .withLastProcessedEventId(Either.Right(LAST_EVENT_ID))
            .withUpdateLastProcessedEventId(event.id, Either.Right(Unit))
            .arrange()

        // When
        eventProcessor.processEvent(event)

        // Then
        verify(arrangement.userEventReceiver)
            .suspendFunction(arrangement.userEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    private companion object {
        const val LAST_EVENT_ID = "last_event_id"
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

        @Mock
        val teamEventReceiver = configure(mock(TeamEventReceiver::class)) { stubsUnitByDefault = true }

        val eventProcessor: EventProcessor =
            EventProcessorImpl(
                eventRepository,
                conversationEventReceiver,
                userEventReceiver,
                teamEventReceiver,
                featureConfigEventReceiver
            )

        suspend fun withUpdateLastProcessedEventId(eventId: String, result: Either<StorageFailure, Unit>) = apply {
            given(eventRepository)
                .coroutine { eventRepository.updateLastProcessedEventId(eventId) }
                .then { result }
        }

        fun arrange() = this to eventProcessor
    }
}
