@file:OptIn(
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
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

package com.wire.kalium.logic.service

import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.events.EventAcknowledgement
import com.wire.kalium.events.EventAcknowledgementRecovery
import com.wire.kalium.events.EventCursor
import com.wire.kalium.events.EventEnvelope
import com.wire.kalium.events.EventIdempotencyKey
import com.wire.kalium.events.EventSource
import com.wire.kalium.events.EventSourceResult
import com.wire.kalium.events.EventStreamResult
import com.wire.kalium.events.PendingEventAcknowledgement
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeData
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.utils.NetworkResponse
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** One aggregate backend notification before Wire payload decoding or decryption. */
@ExperimentalKaliumServiceApi
public data class WireRawEvent(
    public val key: EventIdempotencyKey,
    public val event: EventResponseToStore,
)

/** Consumable-notification event source for a started [JvmServiceNetworkOwner]. */
@ExperimentalKaliumServiceApi
@Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "ReturnCount", "ThrowsCount", "TooManyFunctions")
public class WireNotificationEventSource(
    private val owner: JvmServiceNetworkOwner,
    private val openTimeoutMillis: Long = DEFAULT_OPEN_TIMEOUT_MILLIS,
) : EventSource<WireRawEvent> {
    private val lifecycleMutex = Mutex()
    private val closeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var closed: Boolean = false
    private var activeMarker: String? = null
    private var activeApi: NotificationApi? = null
    private var activeJob: Job? = null
    private var activeChannel: Channel<EventEnvelope<WireRawEvent>>? = null
    private var activeReady: CompletableDeferred<OpenSignal>? = null
    private var inFlight: InFlightDelivery? = null
    private var pendingRecovery: PendingAcknowledgementRecovery? = null
    private val startupRecoveredAcknowledgements = linkedSetOf<EventIdempotencyKey>()
    private var pendingCloseApi: NotificationApi? = null
    private var pendingCloseJob: Job? = null
    private var closeCompleted: Boolean = false

    init {
        require(openTimeoutMillis > 0) { "openTimeoutMillis must be positive" }
    }

    override suspend fun open(from: EventCursor?): EventStreamResult<WireRawEvent> = open(from, emptyList())

    @Suppress("UNUSED_PARAMETER")
    override suspend fun open(
        from: EventCursor?,
        pendingAcknowledgements: List<PendingEventAcknowledgement>,
    ): EventStreamResult<WireRawEvent> {
        val opened = lifecycleMutex.withLock {
            if (closed) return@withLock OpenAttempt.Failure("The notification event source is closed")
            if (activeJob != null) return@withLock OpenAttempt.Failure("The notification event source is already open")
            if (pendingAcknowledgements.size > 1) {
                return@withLock OpenAttempt.Failure(
                    "Consumable notifications cannot have more than one acknowledgement awaiting redelivery",
                )
            }
            val startupRecovery = pendingAcknowledgements.singleOrNull()?.let { pending ->
                if (pending.acknowledgement.recovery != EventAcknowledgementRecovery.WAIT_FOR_REDELIVERY) {
                    return@withLock OpenAttempt.Failure("Only session-scoped acknowledgements can be reconciled on open")
                }
                PendingAcknowledgementRecovery(pending.key, CompletableDeferred())
            }

            val api = try {
                owner.requireNetwork().notificationApi
            } catch (failure: Throwable) {
                return@withLock OpenAttempt.Failure("Authenticated service networking is not ready", failure)
            }
            // No event is submitted until the matching synchronization marker has been observed.
            // Afterwards one delivery at a time is handed to the processor and held until its ACK
            // completes, preventing NotificationApi from replacing the backing session underneath it.
            val channel = Channel<EventEnvelope<WireRawEvent>>(Channel.RENDEZVOUS)
            val ready = CompletableDeferred<OpenSignal>()
            val job = scope.launch {
                runConnectionLoop(api, channel, ready)
            }
            activeApi = api
            activeChannel = channel
            activeReady = ready
            activeJob = job
            pendingRecovery = startupRecovery
            startupRecoveredAcknowledgements.clear()
            OpenAttempt.Started(api, job, channel, ready)
        }

        return when (opened) {
            is OpenAttempt.Failure -> EventStreamResult.Failure(opened.description, opened.cause)
            is OpenAttempt.Started -> {
                val signal = withTimeoutOrNull(openTimeoutMillis) { opened.ready.await() }
                    ?: OpenSignal.Failure("Timed out waiting for the consumable-notification synchronization marker")
                when (signal) {
                    is OpenSignal.Ready -> EventStreamResult.Open(
                        opened.channel.receiveAsFlow(),
                        signal.recoveredAcknowledgements,
                    )
                    is OpenSignal.Failure -> {
                        cleanupFailedOpen(opened, signal.cause)
                        EventStreamResult.Failure(signal.description, signal.cause)
                    }
                }
            }
        }
    }

    override suspend fun acknowledge(acknowledgement: EventAcknowledgement): EventSourceResult {
        val token = acknowledgement.value.toAcknowledgementToken()
            ?: return EventSourceResult.Failure("The consumable-notification acknowledgement token is invalid")
        val active = lifecycleMutex.withLock {
            if (closed) return EventSourceResult.Failure("The notification event source is closed")
            if (activeMarker != token.marker) {
                return EventSourceResult.Failure("The acknowledgement belongs to a different notification session")
            }
            val delivery = inFlight
                ?: return EventSourceResult.Failure("The notification event source has no delivery awaiting acknowledgement")
            if (delivery.marker != token.marker || delivery.deliveryTag != token.deliveryTag) {
                return EventSourceResult.Failure("The acknowledgement does not match the active notification delivery")
            }
            ActiveAcknowledgement(
                api = activeApi
                    ?: return EventSourceResult.Failure("The notification event source is not connected"),
                delivery = delivery,
            )
        }

        when (val submitted = sendAcknowledgement(active.api, token.marker, token.deliveryTag)) {
            EventSourceResult.Success -> {
                active.delivery.resolution.complete(DeliveryResolution.Acknowledged)
                return EventSourceResult.Success
            }
            is EventSourceResult.Failure -> {
                val recovery = PendingAcknowledgementRecovery(
                    key = active.delivery.key,
                    completion = CompletableDeferred(),
                )
                val scheduled = lifecycleMutex.withLock {
                    if (closed || inFlight !== active.delivery || active.delivery.resolution.isCompleted) {
                        false
                    } else {
                        pendingRecovery = recovery
                        active.delivery.resolution.complete(
                            DeliveryResolution.ReconnectForAcknowledgement(recovery, submitted),
                        )
                        true
                    }
                }
                if (!scheduled) return submitted
                return recovery.completion.await()
            }
        }
    }

    override suspend fun close(): EventSourceResult = closeMutex.withLock { closeOnce() }

    internal suspend fun failClosed(description: String, cause: Throwable?) {
        val active = lifecycleMutex.withLock {
            if (closed) return
            closed = true
            inFlight?.resolution?.complete(DeliveryResolution.Closed)
            pendingRecovery?.completion?.complete(EventSourceResult.Failure(description, cause))
            ActiveDelivery(activeApi, activeJob, activeChannel, activeReady)
        }
        val failure = FatalEventTransportException(description, cause)
        active.ready?.complete(OpenSignal.Failure(description, cause))
        active.channel?.close(failure)
        active.job?.cancel(CancellationException(description, failure))
    }

    private suspend fun closeOnce(): EventSourceResult {
        val detached = lifecycleMutex.withLock {
            if (closeCompleted) return EventSourceResult.Success
            closed = true
            inFlight?.resolution?.complete(DeliveryResolution.Closed)
            pendingRecovery?.completion?.complete(EventSourceResult.Failure("The notification event source was closed"))
            val api = pendingCloseApi ?: activeApi
            val job = pendingCloseJob ?: activeJob
            pendingCloseApi = api
            pendingCloseJob = job
            ActiveDelivery(api, job, activeChannel, activeReady).also {
                activeApi = null
                activeMarker = null
                activeJob = null
                activeChannel = null
                activeReady = null
                inFlight = null
                pendingRecovery = null
            }
        }
        detached.ready?.complete(OpenSignal.Failure("The notification event source was closed"))
        detached.job?.cancel()
        val closeResult = detached.api?.let { api ->
            closeLiveEvents(api)
        } ?: EventSourceResult.Success
        val stopped = detached.job?.let { job ->
            withTimeoutOrNull(CLOSE_TIMEOUT_MILLIS) {
                job.join()
                true
            } ?: false
        } ?: true
        detached.channel?.close()
        scope.cancel()
        val result = if (!stopped && closeResult == EventSourceResult.Success) {
            EventSourceResult.Failure("Timed out stopping consumable-notification collection")
        } else {
            closeResult
        }
        lifecycleMutex.withLock {
            if (closeResult == EventSourceResult.Success) pendingCloseApi = null
            if (stopped) pendingCloseJob = null
            closeCompleted = pendingCloseApi == null && pendingCloseJob == null
        }
        return result
    }

    private suspend fun runConnectionLoop(
        api: NotificationApi,
        channel: Channel<EventEnvelope<WireRawEvent>>,
        ready: CompletableDeferred<OpenSignal>,
    ) {
        var consecutiveFailures = 0
        var lastFailure: ConnectionOutcome.Retry? = null
        try {
            while (true) {
                val startedAt = System.nanoTime()
                val outcome = consumeConnection(api, channel, ready)
                when (outcome) {
                    is ConnectionOutcome.Fatal -> {
                        failConnection(outcome.description, outcome.cause, channel, ready)
                        return
                    }
                    is ConnectionOutcome.Retry -> {
                        lastFailure = outcome
                        clearActiveConnection(outcome.marker)
                        when (val closeResult = closeLiveEvents(api)) {
                            is EventSourceResult.Failure -> {
                                val description =
                                    "Cannot reconnect without closing the previous notification session: ${closeResult.description}"
                                failConnection(description, closeResult.cause, channel, ready)
                                return
                            }
                            EventSourceResult.Success -> Unit
                        }
                        val stableConnection = elapsedMillis(startedAt) >= STABLE_CONNECTION_MILLIS
                        consecutiveFailures = if (stableConnection || outcome.madeProgress) 1 else consecutiveFailures + 1
                        if (consecutiveFailures > MAX_CONSECUTIVE_RECONNECT_ATTEMPTS) {
                            val description = "Consumable-notification reconnect limit was exhausted: ${outcome.description}"
                            failConnection(description, outcome.cause, channel, ready)
                            return
                        }
                        delay(reconnectDelayMillis(consecutiveFailures))
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            failConnection("Consumable-notification collection was cancelled", cancellation, channel, ready)
            throw cancellation
        } catch (failure: Throwable) {
            val description = lastFailure?.description ?: "Consumable-notification collection failed"
            failConnection(description, failure, channel, ready)
        }
    }

    private suspend fun consumeConnection(
        api: NotificationApi,
        channel: Channel<EventEnvelope<WireRawEvent>>,
        ready: CompletableDeferred<OpenSignal>,
    ): ConnectionOutcome {
        val marker = UUID.randomUUID().toString()
        val pending = ArrayList<EventEnvelope<WireRawEvent>>(INITIAL_PENDING_EVENT_CAPACITY)
        var pendingPayloadCharacters = 0
        var madeProgress = false
        var isLive = false
        return try {
            publishActiveConnection(api, marker)
            val eventFlow = when (val response = api.consumeLiveEvents(owner.identity.clientId, marker)) {
                is NetworkResponse.Error -> throw EventTransportException(
                    "Failed to open consumable notifications",
                    response.kException,
                )
                is NetworkResponse.Success -> response.value
            }
            eventFlow.collect { socketEvent ->
                when (socketEvent) {
                    is WebSocketEvent.Open -> if (socketEvent.shouldProcessPendingEvents) {
                        throw FatalEventTransportException(
                            "Legacy notification delivery is not supported by this service source",
                        )
                    }

                    is WebSocketEvent.BinaryPayloadReceived -> when (val notification = socketEvent.payload) {
                        is ConsumableNotificationResponse.EventNotification -> {
                            val deliveryTag = notification.data.deliveryTag
                                ?: throw EventTransportException("A consumable event is missing its delivery tag")
                            val envelope = notification.data.event.toEnvelope(marker, deliveryTag, isLive)
                            val recovery = currentRecovery()
                            if (recovery?.key == envelope.key) {
                                when (val result = sendAcknowledgement(api, marker, deliveryTag)) {
                                    is EventSourceResult.Failure -> throw EventTransportException(result.description, result.cause)
                                    EventSourceResult.Success -> {
                                        completeRecovery(recovery, EventSourceResult.Success)
                                        madeProgress = true
                                    }
                                }
                            } else if (isLive) {
                                deliver(envelope, marker, deliveryTag, channel)
                                madeProgress = true
                            } else {
                                val payloadCharacters = notification.data.event.payload?.length ?: 0
                                if (
                                    pending.size >= MAX_PENDING_EVENTS ||
                                    payloadCharacters > MAX_PENDING_PAYLOAD_CHARACTERS - pendingPayloadCharacters
                                ) {
                                    throw FatalEventTransportException(
                                        "Consumable-notification catch-up exceeded its bounded memory limit",
                                    )
                                }
                                pending += envelope
                                pendingPayloadCharacters += payloadCharacters
                            }
                        }

                        is ConsumableNotificationResponse.SynchronizationNotification -> {
                            val deliveryTag = notification.data.deliveryTag
                                ?: throw EventTransportException("A synchronization marker is missing its delivery tag")
                            // This ACK is explicitly non-cumulative (`multiple = false`), so it acknowledges
                            // only the marker. Buffered catch-up deliveries remain unacknowledged until the
                            // event processor handles each one below.
                            when (val result = sendAcknowledgement(api, marker, deliveryTag)) {
                                is EventSourceResult.Failure -> throw EventTransportException(result.description, result.cause)
                                EventSourceResult.Success -> Unit
                            }
                            if (notification.data.markerId == marker) {
                                // If the delivery whose ACK failed is absent from catch-up, the marker proves
                                // the backend accepted the old ACK before the socket failed.
                                currentRecovery()?.let { recovery ->
                                    completeRecovery(recovery, EventSourceResult.Success)
                                    madeProgress = true
                                }
                                isLive = true
                                ready.complete(OpenSignal.Ready(startupRecoveries()))
                                pending.forEach { envelope ->
                                    val token = checkNotNull(envelope.acknowledgement?.value?.toAcknowledgementToken())
                                    deliver(envelope, marker, token.deliveryTag, channel)
                                    madeProgress = true
                                }
                                pending.clear()
                            }
                        }

                        ConsumableNotificationResponse.MissedNotification -> throw FatalEventTransportException(
                            "The backend reported missed notifications; scoped service recovery is not available",
                        )
                    }

                    is WebSocketEvent.NonBinaryPayloadReceived -> throw FatalEventTransportException(
                        "The consumable-notification socket returned a non-binary payload",
                    )

                    is WebSocketEvent.Close -> throw EventTransportException(
                        "The consumable-notification socket closed",
                        socketEvent.cause,
                    )
                }
            }
            ConnectionOutcome.Retry(
                marker,
                "The consumable-notification stream completed unexpectedly",
                null,
                madeProgress,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: FatalEventTransportException) {
            ConnectionOutcome.Fatal(marker, failure.message ?: "Consumable-notification protocol failed", failure)
        } catch (failure: Throwable) {
            val transport = failure as? EventTransportException
            ConnectionOutcome.Retry(
                marker,
                transport?.message ?: "Consumable-notification connection failed",
                failure,
                madeProgress,
            )
        } finally {
            pending.clear()
        }
    }

    private suspend fun deliver(
        envelope: EventEnvelope<WireRawEvent>,
        marker: String,
        deliveryTag: ULong,
        channel: Channel<EventEnvelope<WireRawEvent>>,
    ) {
        val delivery = InFlightDelivery(envelope.key, marker, deliveryTag, CompletableDeferred())
        lifecycleMutex.withLock {
            check(!closed) { "The notification event source is closed" }
            check(activeMarker == marker) { "The notification connection changed before delivery" }
            check(inFlight == null) { "Only one consumable notification may be in flight" }
            inFlight = delivery
        }
        try {
            channel.send(envelope)
            when (val resolution = delivery.resolution.await()) {
                DeliveryResolution.Acknowledged -> Unit
                DeliveryResolution.Closed -> throw CancellationException("The notification event source was closed")
                is DeliveryResolution.ReconnectForAcknowledgement -> throw EventTransportException(
                    resolution.failure.description,
                    resolution.failure.cause,
                )
            }
        } finally {
            lifecycleMutex.withLock {
                if (inFlight === delivery) inFlight = null
            }
        }
    }

    private suspend fun cleanupFailedOpen(opened: OpenAttempt.Started, cause: Throwable?) {
        opened.job.cancel()
        val closeResult = closeLiveEvents(opened.api)
        val stopped = withTimeoutOrNull(CLOSE_TIMEOUT_MILLIS) {
            opened.job.join()
            true
        } ?: false
        opened.channel.close(cause)
        lifecycleMutex.withLock {
            if (activeJob == opened.job) {
                if (!stopped || closeResult is EventSourceResult.Failure) {
                    closed = true
                    if (!stopped) pendingCloseJob = opened.job
                    if (closeResult is EventSourceResult.Failure) pendingCloseApi = opened.api
                }
                activeApi = null
                activeMarker = null
                activeJob = null
                activeChannel = null
                activeReady = null
                inFlight = null
                pendingRecovery = null
            }
        }
    }

    private suspend fun publishActiveConnection(api: NotificationApi, marker: String) {
        lifecycleMutex.withLock {
            check(!closed) { "The notification event source is closed" }
            activeApi = api
            activeMarker = marker
        }
    }

    private suspend fun clearActiveConnection(marker: String) {
        lifecycleMutex.withLock {
            if (activeMarker == marker) activeMarker = null
        }
    }

    private suspend fun currentRecovery(): PendingAcknowledgementRecovery? = lifecycleMutex.withLock { pendingRecovery }

    private suspend fun completeRecovery(
        recovery: PendingAcknowledgementRecovery,
        result: EventSourceResult,
    ) {
        lifecycleMutex.withLock {
            if (pendingRecovery === recovery) {
                if (result == EventSourceResult.Success && activeReady?.isCompleted == false) {
                    startupRecoveredAcknowledgements += recovery.key
                }
                pendingRecovery = null
            }
        }
        recovery.completion.complete(result)
    }

    private suspend fun startupRecoveries(): List<EventIdempotencyKey> = lifecycleMutex.withLock {
        startupRecoveredAcknowledgements.toList()
    }

    private suspend fun failConnection(
        description: String,
        cause: Throwable?,
        channel: Channel<EventEnvelope<WireRawEvent>>,
        ready: CompletableDeferred<OpenSignal>,
    ) {
        val failure = EventTransportException(description, cause)
        lifecycleMutex.withLock {
            activeMarker = null
            inFlight?.resolution?.complete(DeliveryResolution.Closed)
            inFlight = null
            pendingRecovery?.completion?.complete(EventSourceResult.Failure(description, cause))
            pendingRecovery = null
        }
        ready.complete(OpenSignal.Failure(description, cause))
        channel.close(failure)
    }

    private suspend fun closeLiveEvents(api: NotificationApi): EventSourceResult = try {
        withTimeoutOrNull(CLOSE_TIMEOUT_MILLIS) {
            api.closeLiveEvents().toEventSourceResult("Failed to close consumable notifications")
        } ?: EventSourceResult.Failure("Timed out closing consumable notifications")
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        EventSourceResult.Failure("Closing consumable notifications threw an exception", failure)
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

    private fun reconnectDelayMillis(consecutiveFailures: Int): Long {
        val exponent = (consecutiveFailures - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        return (INITIAL_RECONNECT_DELAY_MILLIS shl exponent).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
    }

    private suspend fun sendAcknowledgement(
        api: NotificationApi,
        marker: String,
        deliveryTag: ULong,
    ): EventSourceResult = try {
        withTimeoutOrNull(ACK_SUBMISSION_TIMEOUT_MILLIS) {
            api.acknowledgeEvents(
                clientId = owner.identity.clientId,
                markerId = marker,
                eventAcknowledgeRequest = EventAcknowledgeRequest(
                    type = AcknowledgeType.ACK,
                    data = AcknowledgeData(deliveryTag = deliveryTag, multiple = false),
                ),
            ).toEventSourceResult("Failed to submit a consumable-notification acknowledgement")
        } ?: EventSourceResult.Failure("Timed out submitting a consumable-notification acknowledgement")
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        EventSourceResult.Failure("Submitting a consumable-notification acknowledgement threw an exception", failure)
    }

    private fun EventResponseToStore.toEnvelope(
        marker: String,
        deliveryTag: ULong,
        isLive: Boolean,
    ): EventEnvelope<WireRawEvent> {
        val payloadDigest = calcSHA256(
            "${if (payload == null) NULL_PAYLOAD_MARKER else PRESENT_PAYLOAD_MARKER}\u0000${payload.orEmpty()}\u0000$transient"
                .encodeToByteArray(),
        ).toHex()
        val stableValue = "$id:$payloadDigest"
        val key = EventIdempotencyKey(stableValue)
        return EventEnvelope(
            key = key,
            backendEventId = id,
            cursor = EventCursor(stableValue),
            payload = WireRawEvent(key, this),
            acknowledgement = EventAcknowledgement(
                value = AcknowledgementToken(marker, deliveryTag).encoded,
                recovery = EventAcknowledgementRecovery.WAIT_FOR_REDELIVERY,
            ),
            isLive = isLive,
            isTransient = transient,
        )
    }

    private fun NetworkResponse<Unit>.toEventSourceResult(description: String): EventSourceResult = when (this) {
        is NetworkResponse.Error -> EventSourceResult.Failure(description, kException)
        is NetworkResponse.Success -> EventSourceResult.Success
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { value ->
        (value.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
    }

    private data class AcknowledgementToken(val marker: String, val deliveryTag: ULong) {
        val encoded: String get() = "$marker$TOKEN_SEPARATOR$deliveryTag"
    }

    private fun String.toAcknowledgementToken(): AcknowledgementToken? {
        val separatorIndex = lastIndexOf(TOKEN_SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex == lastIndex) return null
        val marker = substring(0, separatorIndex)
        val deliveryTag = substring(separatorIndex + 1).toULongOrNull() ?: return null
        return AcknowledgementToken(marker, deliveryTag)
    }

    private sealed interface OpenSignal {
        data class Ready(val recoveredAcknowledgements: List<EventIdempotencyKey>) : OpenSignal
        data class Failure(val description: String, val cause: Throwable? = null) : OpenSignal
    }

    private sealed interface OpenAttempt {
        data class Started(
            val api: NotificationApi,
            val job: Job,
            val channel: Channel<EventEnvelope<WireRawEvent>>,
            val ready: CompletableDeferred<OpenSignal>,
        ) : OpenAttempt

        data class Failure(val description: String, val cause: Throwable? = null) : OpenAttempt
    }

    private data class ActiveDelivery(
        val api: NotificationApi?,
        val job: Job?,
        val channel: Channel<EventEnvelope<WireRawEvent>>?,
        val ready: CompletableDeferred<OpenSignal>?,
    )

    private data class ActiveAcknowledgement(
        val api: NotificationApi,
        val delivery: InFlightDelivery,
    )

    private data class InFlightDelivery(
        val key: EventIdempotencyKey,
        val marker: String,
        val deliveryTag: ULong,
        val resolution: CompletableDeferred<DeliveryResolution>,
    )

    private data class PendingAcknowledgementRecovery(
        val key: EventIdempotencyKey,
        val completion: CompletableDeferred<EventSourceResult>,
    )

    private sealed interface DeliveryResolution {
        data object Acknowledged : DeliveryResolution
        data object Closed : DeliveryResolution
        data class ReconnectForAcknowledgement(
            val recovery: PendingAcknowledgementRecovery,
            val failure: EventSourceResult.Failure,
        ) : DeliveryResolution
    }

    private sealed interface ConnectionOutcome {
        val marker: String

        data class Retry(
            override val marker: String,
            val description: String,
            val cause: Throwable?,
            val madeProgress: Boolean,
        ) : ConnectionOutcome

        data class Fatal(
            override val marker: String,
            val description: String,
            val cause: Throwable?,
        ) : ConnectionOutcome
    }

    private class EventTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private class FatalEventTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private companion object {
        const val DEFAULT_OPEN_TIMEOUT_MILLIS = 30_000L
        const val CLOSE_TIMEOUT_MILLIS = 2_000L
        const val ACK_SUBMISSION_TIMEOUT_MILLIS = 5_000L
        const val INITIAL_RECONNECT_DELAY_MILLIS = 250L
        const val MAX_RECONNECT_DELAY_MILLIS = 4_000L
        const val STABLE_CONNECTION_MILLIS = 30_000L
        const val MAX_CONSECUTIVE_RECONNECT_ATTEMPTS = 5
        const val MAX_BACKOFF_EXPONENT = 4
        const val MAX_PENDING_EVENTS = 1_024
        const val MAX_PENDING_PAYLOAD_CHARACTERS = 16 * 1_024 * 1_024
        const val INITIAL_PENDING_EVENT_CAPACITY = 64
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val TOKEN_SEPARATOR = ':'
        const val NULL_PAYLOAD_MARKER = 0
        const val PRESENT_PAYLOAD_MARKER = 1
        const val BYTE_MASK = 0xFF
        const val HEX_RADIX = 16
        const val HEX_BYTE_WIDTH = 2
    }
}
