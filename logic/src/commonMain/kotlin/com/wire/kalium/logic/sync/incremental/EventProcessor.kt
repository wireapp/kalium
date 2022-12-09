package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver

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
    private val teamEventReceiver: TeamEventReceiver,
    private val featureConfigEventReceiver: FeatureConfigEventReceiver,
    private val userPropertiesEventReceiver: UserPropertiesEventReceiver,
    _logger: KaliumLogger
) : EventProcessor {

    private val logger = _logger.withFeatureId(EVENT_RECEIVER)

    override suspend fun processEvent(event: Event) {
        logger.i("Processing event ${event.id.obfuscateId()}")
        when (event) {
            is Event.Conversation -> conversationEventReceiver.onEvent(event)
            is Event.User -> userEventReceiver.onEvent(event)
            is Event.FeatureConfig -> featureConfigEventReceiver.onEvent(event)
            is Event.Unknown -> logger.i("Unhandled event id=${event.id.obfuscateId()}")
            is Event.Team -> teamEventReceiver.onEvent(event)
            is Event.UserProperty -> userPropertiesEventReceiver.onEvent(event)
        }
        logger.i("Updating lastProcessedEventId ${event.id.obfuscateId()}")
        if (event.shouldUpdateLastProcessedEventId()) {
            eventRepository.updateLastProcessedEventId(event.id)
        }
    }
}
