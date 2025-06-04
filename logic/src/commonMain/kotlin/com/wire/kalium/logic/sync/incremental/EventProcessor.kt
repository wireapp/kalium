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
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.MissedNotificationsEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Handles incoming events from remote.
 * @see [Event]
 */
internal interface EventProcessor {

    /**
     * When enabled events will be consumed but no event processing will occur.
     */
    var disableEventProcessing: Boolean

    /**
     * Process the [eventEnvelope], persisting the last processed event ID if the event
     * is not transient (see [EventDeliveryInfo.isTransient]).
     * If the processing fails, the last processed event ID will not be updated.
     * @return [Either] [CoreFailure] if the event processing failed, or [Unit] if the event was processed successfully.
     * @see EventRepository.lastProcessedEventId
     * @see EventDeliveryInfo.isTransient
     * @see EventRepository.updateLastProcessedEventId
     * @see EventDeliveryInfo
     * @see Event
     */
    suspend fun processEvent(eventEnvelope: EventEnvelope): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class EventProcessorImpl(
    private val eventRepository: EventRepository,
    private val conversationEventReceiver: ConversationEventReceiver,
    private val userEventReceiver: UserEventReceiver,
    private val teamEventReceiver: TeamEventReceiver,
    private val featureConfigEventReceiver: FeatureConfigEventReceiver,
    private val userPropertiesEventReceiver: UserPropertiesEventReceiver,
    private val federationEventReceiver: FederationEventReceiver,
    private val missedNotificationsEventReceiver: MissedNotificationsEventReceiver,
    private val processingScope: CoroutineScope,
    logger: KaliumLogger = kaliumLogger,
) : EventProcessor {

    private val logger by lazy {
        logger.withFeatureId(EVENT_RECEIVER)
    }

    override var disableEventProcessing: Boolean = false

    override suspend fun processEvent(eventEnvelope: EventEnvelope): Either<CoreFailure, Unit> = processingScope.async {
        val (event, deliveryInfo) = eventEnvelope
        if (disableEventProcessing) {
            logger.w("Skipping processing of ${event.toLogString()} due to debug option")
            Either.Right(Unit)
        } else {
            logger.i("Starting processing of event: ${event.toLogString()}")
            withContext(NonCancellable) {
                doProcess(event, deliveryInfo, eventEnvelope)
            }
        }
    }.await()

    private suspend fun doProcess(
        event: Event,
        deliveryInfo: EventDeliveryInfo,
        eventEnvelope: EventEnvelope
    ): Either<CoreFailure, Unit> {
        return when (event) {
            is Event.Conversation -> conversationEventReceiver.onEvent(event, deliveryInfo)
            is Event.User -> userEventReceiver.onEvent(event, deliveryInfo)
            is Event.FeatureConfig -> featureConfigEventReceiver.onEvent(event, deliveryInfo)
            is Event.Unknown -> {
                kaliumLogger.createEventProcessingLogger(event)
                    .logComplete(EventLoggingStatus.SKIPPED)
                // Skipping event = success
                Either.Right(Unit)
            }

            is Event.UserProperty -> userPropertiesEventReceiver.onEvent(event, deliveryInfo)
            is Event.Federation -> federationEventReceiver.onEvent(event, deliveryInfo)
            is Event.Team.MemberLeave -> teamEventReceiver.onEvent(event, deliveryInfo)
            is Event.AsyncMissed -> missedNotificationsEventReceiver.onEvent(event, deliveryInfo)
        }.onSuccess {
            // todo (ym) check for errors and decide if lastProcessedEventId should be updated so we can re-ack
            eventRepository.acknowledgeEvent(eventEnvelope)
            if (deliveryInfo.shouldUpdateLastProcessedEventId()) {
                eventRepository.updateLastProcessedEventId(event.id)
                logger.i("Updated lastProcessedEventId: ${eventEnvelope.toLogString()}")
            } else {
                logger.i("Skipping update of lastProcessedEventId: ${eventEnvelope.toLogString()}")
            }
        }
    }

    private fun EventDeliveryInfo.shouldUpdateLastProcessedEventId(): Boolean = !isTransient
}
