@file:OptIn(com.wire.kalium.events.ExperimentalEventApi::class)

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

package com.wire.kalium.events

import kotlinx.coroutines.flow.Flow

/**
 * An opaque idempotency key for one delivered payload.
 *
 * Sources must not assume a backend event ID is unique. They should include the delivery identity,
 * payload position/hash, or another stable discriminator when constructing this key.
 */
@ExperimentalEventApi
public data class EventIdempotencyKey(public val value: String)

/** An opaque position from which an event source can resume delivery. */
@ExperimentalEventApi
public data class EventCursor(public val value: String)

@ExperimentalEventApi
public enum class EventAcknowledgementRecovery {
    /** The acknowledgement token is valid after reconnect or process restart. */
    REPLAY,

    /** The source must wait for redelivery and acknowledge the new session-scoped token. */
    WAIT_FOR_REDELIVERY,
}

/** An opaque token understood only by the event source acknowledgement adapter. */
@ExperimentalEventApi
public data class EventAcknowledgement(
    public val value: String,
    public val recovery: EventAcknowledgementRecovery = EventAcknowledgementRecovery.REPLAY,
)

/** A raw event together with the state needed for restart-safe delivery. */
@ExperimentalEventApi
public data class EventEnvelope<out RawEvent>(
    public val key: EventIdempotencyKey,
    public val backendEventId: String?,
    public val cursor: EventCursor,
    public val payload: RawEvent,
    public val acknowledgement: EventAcknowledgement?,
    public val isLive: Boolean,
    public val isTransient: Boolean,
)

@ExperimentalEventApi
public sealed interface EventSourceResult {
    public data object Success : EventSourceResult

    public data class Failure(public val description: String, public val cause: Throwable? = null) : EventSourceResult
}

@ExperimentalEventApi
public sealed interface EventStreamResult<out RawEvent> {
    /** A stream opened after authenticated connection and initial synchronization are ready. */
    public data class Open<RawEvent>(public val events: Flow<EventEnvelope<RawEvent>>) : EventStreamResult<RawEvent>

    public data class Failure(public val description: String, public val cause: Throwable? = null) :
        EventStreamResult<Nothing>
}

/**
 * Authenticated, reconnecting event delivery.
 *
 * Implementations own HTTP/WebSocket protocol details. They must not acknowledge an envelope
 * before [acknowledge] is called by the event processor.
 */
@ExperimentalEventApi
public interface EventSource<out RawEvent> {
    /**
     * Opens authenticated delivery from [from]. Success is returned only after the WebSocket (or
     * equivalent source) and its initial synchronization marker are ready.
     */
    public suspend fun open(from: EventCursor?): EventStreamResult<RawEvent>

    public suspend fun acknowledge(acknowledgement: EventAcknowledgement): EventSourceResult

    public suspend fun close(): EventSourceResult
}

@ExperimentalEventApi
public sealed interface EventDeliveryStateResult<out Value> {
    public data class Success<Value>(public val value: Value) : EventDeliveryStateResult<Value>

    public data class Failure(public val description: String, public val cause: Throwable? = null) :
        EventDeliveryStateResult<Nothing>
}

/** An acknowledgement that was durably scheduled after all required handlers completed. */
@ExperimentalEventApi
public data class PendingEventAcknowledgement(
    public val key: EventIdempotencyKey,
    public val acknowledgement: EventAcknowledgement,
)

/** Restart state loaded before the event source is opened. */
@ExperimentalEventApi
public data class EventDeliveryState(
    public val acknowledgedCursor: EventCursor?,
    public val pendingAcknowledgements: List<PendingEventAcknowledgement>,
)

/**
 * Durable checkpoint and idempotency state for one Wire identity.
 *
 * [recordHandled] must atomically record the idempotency key, cursor, acknowledgement token, and
 * checkpoint eligibility. It must not advance the acknowledged cursor. [recordAcknowledged]
 * advances the cursor only after transport acknowledgement succeeds and only when the recorded
 * event is checkpoint-eligible. All operations must be idempotent.
 *
 * Service compositions require a real implementation; this API intentionally provides no no-op
 * default.
 */
@ExperimentalEventApi
public interface EventDeliveryStateStore {
    public suspend fun loadState(): EventDeliveryStateResult<EventDeliveryState>

    public suspend fun isHandled(key: EventIdempotencyKey): EventDeliveryStateResult<Boolean>

    public suspend fun recordHandled(
        key: EventIdempotencyKey,
        cursor: EventCursor,
        acknowledgement: EventAcknowledgement?,
        advancesCheckpoint: Boolean,
    ): EventDeliveryStateResult<Unit>

    public suspend fun recordAcknowledged(key: EventIdempotencyKey): EventDeliveryStateResult<Unit>
}
