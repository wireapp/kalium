package com.wire.kalium.logic.sync.event

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.EventReceiver

/**
 * Handles incoming events from remote.
 * @see [Event]
 */
internal interface EventProcessor {
    /**
     * Process the [event], and persist the last processed event ID.
     * @see EventRepository.lastEventId
     * @see EventRepository.updateLastProcessedEventId
     */
    suspend fun processEvent(event: Event)
}

interface ConversationEventReceiver : EventReceiver<Event.Conversation>
interface UserEventReceiver : EventReceiver<Event.User>

internal class EventProcessorImpl(
    private val eventRepository: EventRepository,
    private val conversationEventReceiver: ConversationEventReceiver,
    private val userEventReceiver: UserEventReceiver
) : EventProcessor {

    override suspend fun processEvent(event: Event) {
        kaliumLogger.i(message = "SYNC: Processing event ${event.id}")
        when (event) {
            is Event.Conversation -> conversationEventReceiver.onEvent(event)
            is Event.User -> userEventReceiver.onEvent(event)
            is Event.Unknown -> kaliumLogger.i("Unhandled event id=${event.id}")
        }
        eventRepository.updateLastProcessedEventId(event.id)
    }
}
