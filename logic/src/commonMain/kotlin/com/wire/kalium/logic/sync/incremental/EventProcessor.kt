/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.util.serialization.toJsonElement

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
) : EventProcessor {

    private val logger by lazy {
        kaliumLogger.withFeatureId(EVENT_RECEIVER)
    }

    override suspend fun processEvent(event: Event) {
        when (event) {
            is Event.Conversation -> conversationEventReceiver.onEvent(event)
            is Event.User -> userEventReceiver.onEvent(event)
            is Event.FeatureConfig -> featureConfigEventReceiver.onEvent(event)
            is Event.Unknown -> {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SKIPPED,
                        event
                    )
            }
            is Event.Team -> teamEventReceiver.onEvent(event)
            is Event.UserProperty -> userPropertiesEventReceiver.onEvent(event)
        }

        val logMap = mapOf<String, Any>(
            "event" to event.toLogMap()
        )

        if (event.shouldUpdateLastProcessedEventId()) {
            eventRepository.updateLastProcessedEventId(event.id)
            logger.i("Updated lastProcessedEventId: ${logMap.toJsonElement()}")
        } else {
            logger.i("Skipping update of lastProcessedEventId: ${logMap.toJsonElement()}")
        }
    }
}
