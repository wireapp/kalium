@file:OptIn(
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.event.processing.ExperimentalEventProcessingApi::class,
)
@file:Suppress("TooGenericExceptionCaught")

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.event.processing

import com.wire.kalium.events.EventAcknowledgementRecovery
import com.wire.kalium.events.EventCursor
import com.wire.kalium.events.EventDeliveryState
import com.wire.kalium.events.EventDeliveryStateResult
import com.wire.kalium.events.EventDeliveryStateStore
import com.wire.kalium.events.EventEnvelope
import com.wire.kalium.events.EventIdempotencyKey
import com.wire.kalium.events.EventSource
import com.wire.kalium.events.EventSourceResult
import com.wire.kalium.events.EventStreamResult
import com.wire.kalium.events.PendingEventAcknowledgement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

@ExperimentalEventProcessingApi
public sealed interface EventTransformationResult<out Value> {
    public data class Success<Value>(public val value: Value) : EventTransformationResult<Value>

    public data class Failure(public val description: String, public val cause: Throwable? = null) :
        EventTransformationResult<Nothing>
}

@ExperimentalEventProcessingApi
public fun interface EventDecoder<in RawEvent, out DecodedEvent> {
    public suspend fun decode(rawEvent: RawEvent): EventTransformationResult<DecodedEvent>
}

@ExperimentalEventProcessingApi
public fun interface EventDecryptor<in DecodedEvent, out DecryptedEvent> {
    public suspend fun decrypt(decodedEvent: DecodedEvent): EventTransformationResult<DecryptedEvent>
}

@ExperimentalEventProcessingApi
public enum class EventHandlerRequirement {
    REQUIRED,
    OPTIONAL,
}

@ExperimentalEventProcessingApi
public data class EventHandlingContext(
    public val idempotencyKey: EventIdempotencyKey,
    public val backendEventId: String?,
)

@ExperimentalEventProcessingApi
public sealed interface EventHandlingResult {
    public data object Handled : EventHandlingResult

    public data object Ignored : EventHandlingResult

    public data class Failed(public val description: String, public val cause: Throwable? = null) : EventHandlingResult
}

/**
 * Handlers run sequentially in registration order and have at-least-once invocation semantics.
 * Implementations with side effects must use [EventHandlingContext.idempotencyKey] to deduplicate.
 */
@ExperimentalEventProcessingApi
public interface EventHandler<in Event> {
    public val requirement: EventHandlerRequirement

    public fun accepts(event: Event): Boolean

    public suspend fun handle(event: Event, context: EventHandlingContext): EventHandlingResult
}

@ExperimentalEventProcessingApi
public sealed interface EventRoutingResult {
    public data class Completed(
        public val handledCount: Int,
        public val optionalFailures: List<EventHandlingResult.Failed>,
    ) : EventRoutingResult

    public data class RequiredHandlerFailed(public val failure: EventHandlingResult.Failed) : EventRoutingResult
}

@ExperimentalEventProcessingApi
public class EventRouter<Event>(handlers: List<EventHandler<Event>>) {
    private val orderedHandlers: List<EventHandler<Event>> = handlers.toList()

    public suspend fun route(event: Event, context: EventHandlingContext): EventRoutingResult {
        var handledCount = 0
        val optionalFailures = mutableListOf<EventHandlingResult.Failed>()
        orderedHandlers.forEach { handler ->
            if (!handler.accepts(event)) return@forEach
            val result = try {
                handler.handle(event, context)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                EventHandlingResult.Failed("Event handler threw an exception", failure)
            }
            when (result) {
                EventHandlingResult.Handled -> handledCount += 1
                EventHandlingResult.Ignored -> Unit
                is EventHandlingResult.Failed -> when (handler.requirement) {
                    EventHandlerRequirement.REQUIRED -> return EventRoutingResult.RequiredHandlerFailed(result)
                    EventHandlerRequirement.OPTIONAL -> optionalFailures += result
                }
            }
        }
        return EventRoutingResult.Completed(handledCount, optionalFailures)
    }
}

@ExperimentalEventProcessingApi
public sealed interface EventProcessingFailure {
    public data class Source(public val description: String, public val cause: Throwable? = null) : EventProcessingFailure

    public data class DeliveryState(public val description: String, public val cause: Throwable? = null) : EventProcessingFailure

    public data class Decode(public val description: String, public val cause: Throwable? = null) : EventProcessingFailure

    public data class Decrypt(public val description: String, public val cause: Throwable? = null) : EventProcessingFailure

    public data class RequiredHandler(public val description: String, public val cause: Throwable? = null) : EventProcessingFailure

    public data class Acknowledgement(public val description: String, public val cause: Throwable? = null) : EventProcessingFailure
}

@ExperimentalEventProcessingApi
public sealed interface EventProcessingOutcome {
    /** Durable delivery state is loaded and pending acknowledgements have been recovered. */
    public data class Ready(public val acknowledgedCursor: EventCursor?) : EventProcessingOutcome

    public data class Processed(
        public val key: EventIdempotencyKey,
        public val handledCount: Int,
        public val optionalFailures: List<EventHandlingResult.Failed>,
    ) : EventProcessingOutcome

    public data class Duplicate(public val key: EventIdempotencyKey) : EventProcessingOutcome

    public data class AcknowledgementRecovered(public val key: EventIdempotencyKey) : EventProcessingOutcome

    public data class Failed(public val key: EventIdempotencyKey?, public val failure: EventProcessingFailure) : EventProcessingOutcome
}

/**
 * Storage-neutral decode/decrypt/route pipeline.
 *
 * Required handlers complete before handled state is recorded. The acknowledgement intent is
 * stored durably before the transport is acknowledged, and the resume cursor advances only after
 * that acknowledgement succeeds. Pending acknowledgements are recovered before delivery starts.
 */
@ExperimentalEventProcessingApi
public class EventProcessor<RawEvent, DecodedEvent, DecryptedEvent>(
    private val source: EventSource<RawEvent>,
    private val deliveryState: EventDeliveryStateStore,
    private val decoder: EventDecoder<RawEvent, DecodedEvent>,
    private val decryptor: EventDecryptor<DecodedEvent, DecryptedEvent>,
    private val router: EventRouter<DecryptedEvent>,
) {
    @Suppress("CyclomaticComplexMethod")
    public fun process(): Flow<EventProcessingOutcome> = flow {
        val initialState = when (val result = loadState()) {
            is StateCall.Success -> result.value
            is StateCall.Failure -> {
                emit(EventProcessingOutcome.Failed(null, result.failure))
                return@flow
            }
        }

        for (pending in initialState.pendingAcknowledgements) {
            if (pending.acknowledgement.recovery == EventAcknowledgementRecovery.WAIT_FOR_REDELIVERY) continue
            val failure = recoverAcknowledgement(pending)
            if (failure != null) {
                emit(failure)
                return@flow
            }
            emit(EventProcessingOutcome.AcknowledgementRecovered(pending.key))
        }

        val stream = try {
            source.open(initialState.acknowledgedCursor)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            EventStreamResult.Failure("Event source open threw an exception", failure)
        }
        val events = when (stream) {
            is EventStreamResult.Open -> stream.events
            is EventStreamResult.Failure -> {
                emit(EventProcessingOutcome.Failed(null, EventProcessingFailure.Source(stream.description, stream.cause)))
                return@flow
            }
        }

        emit(EventProcessingOutcome.Ready(initialState.acknowledgedCursor))

        try {
            events.collect { envelope ->
                val outcome = processEnvelope(envelope)
                emit(outcome)
                if (outcome is EventProcessingOutcome.Failed) throw StopEventProcessing
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: StopEventProcessingException) {
            // The failed outcome has already been emitted. Collection stops to force redelivery.
        } catch (failure: Throwable) {
            emit(EventProcessingOutcome.Failed(null, EventProcessingFailure.Source("Event source failed", failure)))
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private suspend fun processEnvelope(envelope: EventEnvelope<RawEvent>): EventProcessingOutcome {
        when (val handled = isHandled(envelope.key)) {
            is StateCall.Failure -> return EventProcessingOutcome.Failed(envelope.key, handled.failure)
            is StateCall.Success -> if (handled.value) return finishAcknowledgement(envelope, duplicate = true)
        }

        val decoded = try {
            decoder.decode(envelope.payload)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            EventTransformationResult.Failure("Event decoder threw an exception", failure)
        }
        val decodedEvent = when (decoded) {
            is EventTransformationResult.Success -> decoded.value
            is EventTransformationResult.Failure -> return EventProcessingOutcome.Failed(
                envelope.key,
                EventProcessingFailure.Decode(decoded.description, decoded.cause),
            )
        }
        val decrypted = try {
            decryptor.decrypt(decodedEvent)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            EventTransformationResult.Failure("Event decryptor threw an exception", failure)
        }
        val decryptedEvent = when (decrypted) {
            is EventTransformationResult.Success -> decrypted.value
            is EventTransformationResult.Failure -> return EventProcessingOutcome.Failed(
                envelope.key,
                EventProcessingFailure.Decrypt(decrypted.description, decrypted.cause),
            )
        }
        val routed = when (
            val result = router.route(
                decryptedEvent,
                EventHandlingContext(envelope.key, envelope.backendEventId),
            )
        ) {
            is EventRoutingResult.Completed -> result
            is EventRoutingResult.RequiredHandlerFailed -> return EventProcessingOutcome.Failed(
                envelope.key,
                EventProcessingFailure.RequiredHandler(result.failure.description, result.failure.cause),
            )
        }
        when (val result = recordHandled(envelope)) {
            is StateCall.Failure -> return EventProcessingOutcome.Failed(envelope.key, result.failure)
            is StateCall.Success -> Unit
        }
        return finishAcknowledgement(envelope, duplicate = false, routed = routed)
    }

    @Suppress("ReturnCount")
    private suspend fun finishAcknowledgement(
        envelope: EventEnvelope<RawEvent>,
        duplicate: Boolean,
        routed: EventRoutingResult.Completed? = null,
    ): EventProcessingOutcome {
        envelope.acknowledgement?.let { acknowledgement ->
            when (val result = acknowledge(acknowledgement)) {
                is SourceCall.Failure -> return EventProcessingOutcome.Failed(envelope.key, result.failure)
                SourceCall.Success -> Unit
            }
        }
        when (val result = recordAcknowledged(envelope.key)) {
            is StateCall.Failure -> return EventProcessingOutcome.Failed(envelope.key, result.failure)
            is StateCall.Success -> Unit
        }
        return if (duplicate) {
            EventProcessingOutcome.Duplicate(envelope.key)
        } else {
            checkNotNull(routed)
            EventProcessingOutcome.Processed(envelope.key, routed.handledCount, routed.optionalFailures)
        }
    }

    private suspend fun recoverAcknowledgement(pending: PendingEventAcknowledgement): EventProcessingOutcome.Failed? {
        when (val result = acknowledge(pending.acknowledgement)) {
            is SourceCall.Failure -> return EventProcessingOutcome.Failed(pending.key, result.failure)
            SourceCall.Success -> Unit
        }
        return when (val result = recordAcknowledged(pending.key)) {
            is StateCall.Failure -> EventProcessingOutcome.Failed(pending.key, result.failure)
            is StateCall.Success -> null
        }
    }

    private suspend fun loadState(): StateCall<EventDeliveryState> = stateCall { deliveryState.loadState() }

    private suspend fun isHandled(key: EventIdempotencyKey): StateCall<Boolean> = stateCall { deliveryState.isHandled(key) }

    private suspend fun recordHandled(envelope: EventEnvelope<RawEvent>): StateCall<Unit> = stateCall {
        deliveryState.recordHandled(
            envelope.key,
            envelope.cursor,
            envelope.acknowledgement,
            advancesCheckpoint = !envelope.isTransient,
        )
    }

    private suspend fun recordAcknowledged(key: EventIdempotencyKey): StateCall<Unit> = stateCall {
        deliveryState.recordAcknowledged(key)
    }

    private suspend fun <Value> stateCall(block: suspend () -> EventDeliveryStateResult<Value>): StateCall<Value> = try {
        when (val result = block()) {
            is EventDeliveryStateResult.Success -> StateCall.Success(result.value)
            is EventDeliveryStateResult.Failure -> StateCall.Failure(
                EventProcessingFailure.DeliveryState(result.description, result.cause),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        StateCall.Failure(EventProcessingFailure.DeliveryState("Delivery state store threw an exception", failure))
    }

    private suspend fun acknowledge(acknowledgement: com.wire.kalium.events.EventAcknowledgement): SourceCall = try {
        when (val result = source.acknowledge(acknowledgement)) {
            EventSourceResult.Success -> SourceCall.Success
            is EventSourceResult.Failure -> SourceCall.Failure(
                EventProcessingFailure.Acknowledgement(result.description, result.cause),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        SourceCall.Failure(EventProcessingFailure.Acknowledgement("Event acknowledgement threw an exception", failure))
    }

    private data object StopEventProcessing : StopEventProcessingException()
}

private sealed interface StateCall<out Value> {
    data class Success<Value>(val value: Value) : StateCall<Value>
    data class Failure(val failure: EventProcessingFailure.DeliveryState) : StateCall<Nothing>
}

private sealed interface SourceCall {
    data object Success : SourceCall
    data class Failure(val failure: EventProcessingFailure.Acknowledgement) : SourceCall
}

private open class StopEventProcessingException : Exception()
