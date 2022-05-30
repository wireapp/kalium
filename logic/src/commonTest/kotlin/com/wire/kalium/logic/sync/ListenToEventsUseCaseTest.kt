package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.conversation.ConversationMembers
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

interface ConversationEventReceiver : EventReceiver<Event.Conversation>

class ListenToEventsUseCaseTest {

    @Mock
    val syncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

    @Mock
    val eventRepository = configure(mock(EventRepository::class)) { stubsUnitByDefault = true }

    @Mock
    val conversationEventReceiver = configure(mock(ConversationEventReceiver::class)) { stubsUnitByDefault = true }

    lateinit var listenToEvents: ListenToEventsUseCase

    @BeforeTest
    fun setup() {
        listenToEvents = ListenToEventsUseCase(syncManager, eventRepository, conversationEventReceiver)
    }

    @Test
    fun givenAnEventIsReceived_whenListeningToEvents_thenTheLastProcessedEventIdIsUpdated() = runTest {
        val event = Event.Conversation.MemberJoin(
            "firstEventId",
            TestConversation.ID,
            TestUser.USER_ID,
            listOf(),
            "2022-03-30T15:36:00.000Z"
        )
        val events = listOf(Either.Right(event))

        given(eventRepository)
            .suspendFunction(eventRepository::events)
            .whenInvoked()
            .thenReturn(events.asFlow())

        listenToEvents()

        verify(eventRepository)
            .suspendFunction(eventRepository::updateLastProcessedEventId)
            .with(eq(event.id))
            .wasInvoked(exactly = once)
    }

}
