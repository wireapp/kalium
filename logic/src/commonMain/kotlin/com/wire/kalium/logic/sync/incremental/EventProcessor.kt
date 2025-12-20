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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Handles processing of incoming [Event]s received via sync or WebSocket.
 *
 * Each event is wrapped in an [EventEnvelope], containing both the event and its delivery metadata.
 * This interface allows disabling processing during testing or debug scenarios.
 *
 * Events are dispatched to corresponding domain-specific receivers (e.g., `ConversationEventReceiver`)
 * and marked as processed via [EventRepository] if processing is successful.
 *
 * @see EventEnvelope
 * @see EventDeliveryInfo
 * @see EventRepository
 */
@Mockable
internal interface EventProcessor {

    /**
     * When enabled events will be consumed but no event processing will occur.
     */
    var disableEventProcessing: Boolean

    /**
     * Processes a single [eventEnvelope] using the provided [transactionContext].
     *
     * The underlying event is dispatched to the appropriate domain receiver (e.g., conversations, users).
     * If the event is not transient, it is marked as processed via [EventRepository].
     *
     * If processing fails, no updates will be persisted to the event store.
     *
     * @param transactionContext Contains access to Proteus or MLS cryptographic context.
     * @param eventEnvelope The envelope containing the event and its metadata.
     * @return Either a [CoreFailure] if processing failed, or [Unit] if processing succeeded.
     */
    suspend fun processEvent(
        transactionContext: CryptoTransactionContext,
        eventEnvelope: EventEnvelope
    ): Either<CoreFailure, Unit>
}

/**
 * Callback interface for notifications about event processing.
 */
interface EventProcessingCallback {
    /**
     * Called after an event is successfully processed.
     */
    fun onEventProcessed()
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
    private val processingScope: CoroutineScope,
    private val eventProcessingCallback: EventProcessingCallback? = null,
    logger: KaliumLogger = kaliumLogger,
) : EventProcessor {

    private val logger by lazy {
        logger.withFeatureId(EVENT_RECEIVER)
    }

    override var disableEventProcessing: Boolean = false

    override suspend fun processEvent(
        transactionContext: CryptoTransactionContext,
        eventEnvelope: EventEnvelope
    ): Either<CoreFailure, Unit> = processingScope.async {
        val (event, deliveryInfo) = eventEnvelope
        if (disableEventProcessing) {
            logger.w("Skipping processing of ${event.toLogString()} due to debug option")
            Either.Right(Unit)
        } else {
            logger.i("Starting processing of event: ${event.toLogString()}")
            withContext(NonCancellable) {
                doProcess(transactionContext, event, deliveryInfo, eventEnvelope)
            }
        }
    }.await()

    private suspend fun doProcess(
        transactionContext: CryptoTransactionContext,
        event: Event,
        deliveryInfo: EventDeliveryInfo,
        eventEnvelope: EventEnvelope
    ): Either<CoreFailure, Unit> {
        return when (event) {
            is Event.Conversation -> conversationEventReceiver.onEvent(transactionContext, event, deliveryInfo)
            is Event.User -> userEventReceiver.onEvent(transactionContext, event, deliveryInfo)
            is Event.FeatureConfig -> featureConfigEventReceiver.onEvent(transactionContext, event, deliveryInfo)
            is Event.Unknown -> {
                kaliumLogger.createEventProcessingLogger(event)
                    .logComplete(EventLoggingStatus.SKIPPED)
                // Skipping event = success
                Either.Right(Unit)
            }

            is Event.UserProperty -> userPropertiesEventReceiver.onEvent(transactionContext, event, deliveryInfo)
            is Event.Federation -> federationEventReceiver.onEvent(transactionContext, event, deliveryInfo)
            is Event.Team.MemberLeave -> teamEventReceiver.onEvent(transactionContext, event, deliveryInfo)
        }.onSuccess {
            eventRepository.setEventAsProcessed(event.id).onSuccess {
                logger.i("Event set as processed: ${eventEnvelope.toLogString()}")
                // Notify callback that event was processed
                eventProcessingCallback?.onEventProcessed()
            }
        }
    }
}
