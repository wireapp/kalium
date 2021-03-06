package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver

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

internal class EventProcessorImpl(
    private val eventRepository: EventRepository,
    private val conversationEventReceiver: ConversationEventReceiver,
    private val userEventReceiver: UserEventReceiver,
    private val featureConfigEventReceiver: FeatureConfigEventReceiver,
) : EventProcessor {

    override suspend fun processEvent(event: Event) {
        kaliumLogger.withFeatureId(EVENT_RECEIVER).i(message = "SYNC: Processing event ${event.id}")
        when (event) {
            is Event.Conversation -> conversationEventReceiver.onEvent(event)
            is Event.User -> userEventReceiver.onEvent(event)
            is Event.FeatureConfig -> featureConfigEventReceiver.onEvent(event)
            is Event.Unknown -> kaliumLogger.withFeatureId(EVENT_RECEIVER).i("Unhandled event id=${event.id}")
        }
        eventRepository.updateLastProcessedEventId(event.id)
    }
}
