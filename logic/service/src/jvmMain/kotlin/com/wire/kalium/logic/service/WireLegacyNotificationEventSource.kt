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
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.utils.NetworkResponse
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

/** Legacy pending-notification catch-up plus live WebSocket delivery with durable local checkpoints. */
@Suppress("TooManyFunctions")
internal class WireLegacyNotificationEventSource(
    private val owner: JvmServiceNetworkOwner,
    private val openTimeoutMillis: Long,
) : EventSource<WireRawEvent> {
    private val lifecycleMutex = Mutex()
    private val closeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastAcknowledgedCursor: String? = null

    private var closed = false
    private var activeApi: NotificationApi? = null
    private var activeJob: Job? = null
    private var activeChannel: Channel<EventEnvelope<WireRawEvent>>? = null
    private var activeReady: CompletableDeferred<OpenSignal>? = null

    init {
        require(openTimeoutMillis > 0) { "openTimeoutMillis must be positive" }
    }

    override suspend fun open(from: EventCursor?): EventStreamResult<WireRawEvent> = open(from, emptyList())

    override suspend fun open(
        from: EventCursor?,
        pendingAcknowledgements: List<PendingEventAcknowledgement>,
    ): EventStreamResult<WireRawEvent> {
        if (pendingAcknowledgements.isNotEmpty()) {
            return EventStreamResult.Failure("Legacy notification delivery received an unsupported session acknowledgement")
        }
        if (lastAcknowledgedCursor == null) lastAcknowledgedCursor = from?.value
        val attempt = lifecycleMutex.withLock {
            if (closed) return@withLock OpenAttempt.Failure("The legacy notification event source is closed")
            if (activeJob != null) return@withLock OpenAttempt.Failure("The legacy notification event source is already open")
            val api = try {
                owner.requireNetwork().notificationApi
            } catch (failure: Throwable) {
                return@withLock OpenAttempt.Failure("Authenticated service networking is not ready", failure)
            }
            val channel = Channel<EventEnvelope<WireRawEvent>>(Channel.RENDEZVOUS)
            val ready = CompletableDeferred<OpenSignal>()
            val job = scope.launch { runConnectionLoop(api, channel, ready) }
            activeApi = api
            activeJob = job
            activeChannel = channel
            activeReady = ready
            OpenAttempt.Started(job, channel, ready)
        }
        return when (attempt) {
            is OpenAttempt.Failure -> EventStreamResult.Failure(attempt.description, attempt.cause)
            is OpenAttempt.Started -> when (
                val signal = withTimeoutOrNull(openTimeoutMillis) { attempt.ready.await() }
                    ?: OpenSignal.Failure("Timed out opening legacy incremental notifications")
            ) {
                OpenSignal.Ready -> EventStreamResult.Open(attempt.channel.receiveAsFlow())
                is OpenSignal.Failure -> {
                    attempt.job.cancel()
                    EventStreamResult.Failure(signal.description, signal.cause)
                }
            }
        }
    }

    override suspend fun acknowledge(acknowledgement: EventAcknowledgement): EventSourceResult {
        val persistentCursor = acknowledgement.value.removePrefix(PERSISTENT_ACK_PREFIX)
            .takeIf { acknowledgement.value.startsWith(PERSISTENT_ACK_PREFIX) && it.isNotBlank() }
        if (persistentCursor != null) {
            lastAcknowledgedCursor = persistentCursor
            return EventSourceResult.Success
        }
        val transientCursor = acknowledgement.value.removePrefix(TRANSIENT_ACK_PREFIX)
            .takeIf { acknowledgement.value.startsWith(TRANSIENT_ACK_PREFIX) && it.isNotBlank() }
        return if (transientCursor != null) {
            EventSourceResult.Success
        } else {
            EventSourceResult.Failure("The legacy notification checkpoint token is invalid")
        }
    }

    override suspend fun close(): EventSourceResult = closeMutex.withLock {
        val active = lifecycleMutex.withLock {
            if (closed && activeJob == null) return@withLock ActiveDelivery(null, null, null)
            closed = true
            ActiveDelivery(activeApi, activeJob, activeChannel).also {
                activeApi = null
                activeJob = null
                activeChannel = null
                activeReady = null
            }
        }
        active.job?.cancel()
        val closeResult = active.api?.closeLiveEvents()?.toEventSourceResult("Failed to close legacy notifications")
            ?: EventSourceResult.Success
        val stopped = active.job?.let { job ->
            withTimeoutOrNull(CLOSE_TIMEOUT_MILLIS) {
                job.join()
                true
            } ?: false
        } ?: true
        active.channel?.close()
        scope.cancel()
        if (!stopped && closeResult == EventSourceResult.Success) {
            EventSourceResult.Failure("Timed out stopping legacy notification collection")
        } else {
            closeResult
        }
    }

    internal suspend fun failClosed(description: String, cause: Throwable?) {
        val active = lifecycleMutex.withLock {
            if (closed) return
            closed = true
            ActiveDelivery(activeApi, activeJob, activeChannel).also {
                activeApi = null
                activeJob = null
                activeChannel = null
                activeReady?.complete(OpenSignal.Failure(description, cause))
                activeReady = null
            }
        }
        active.job?.cancel(CancellationException(description, cause))
        active.channel?.close(EventTransportException(description, cause))
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "ThrowsCount")
    private suspend fun runConnectionLoop(
        api: NotificationApi,
        channel: Channel<EventEnvelope<WireRawEvent>>,
        ready: CompletableDeferred<OpenSignal>,
    ) {
        var consecutiveFailures = 0
        while (true) {
            val startedAt = System.nanoTime()
            var madeProgress = false
            try {
                val events = when (val response = api.listenToLiveEvents(owner.identity.clientId)) {
                    is NetworkResponse.Error -> throw EventTransportException(
                        "Failed to open the legacy notification WebSocket",
                        response.kException,
                    )
                    is NetworkResponse.Success -> response.value
                }
                var opened = false
                events.collect { event ->
                    when (event) {
                        is WebSocketEvent.Open -> {
                            opened = true
                            val pending = fetchPending(api, lastAcknowledgedCursor)
                            ready.complete(OpenSignal.Ready)
                            pending.forEach {
                                channel.send(it.toEnvelope(isLive = false))
                                madeProgress = true
                            }
                        }
                        is WebSocketEvent.BinaryPayloadReceived -> {
                            if (!opened) throw EventTransportException("Legacy notification payload arrived before WebSocket open")
                            channel.send(event.payload.toEnvelope(isLive = true))
                            madeProgress = true
                        }
                        is WebSocketEvent.NonBinaryPayloadReceived -> throw EventTransportException(
                            "The legacy notification WebSocket returned a non-binary payload",
                        )
                        is WebSocketEvent.Close -> throw EventTransportException(
                            "The legacy notification WebSocket closed",
                            event.cause,
                        )
                    }
                }
                throw EventTransportException("The legacy notification stream completed unexpectedly")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                val stableConnection = elapsedMillis(startedAt) >= STABLE_CONNECTION_MILLIS
                consecutiveFailures = if (stableConnection || madeProgress) 1 else consecutiveFailures + 1
                runCatching { api.closeLiveEvents() }
                if (consecutiveFailures > MAX_CONSECUTIVE_RECONNECT_ATTEMPTS) {
                    val description = "Legacy notification reconnect limit was exhausted"
                    ready.complete(OpenSignal.Failure(description, failure))
                    channel.close(EventTransportException(description, failure))
                    return
                }
                delay(reconnectDelayMillis(consecutiveFailures))
            }
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun fetchPending(api: NotificationApi, from: String?): List<EventResponseToStore> {
        val pending = ArrayList<EventResponseToStore>(INITIAL_PENDING_EVENT_CAPACITY)
        var cursor = from
        var hasMore = true
        var payloadCharacters = 0
        while (hasMore) {
            val page = when (val response = nextPendingPage(api, cursor)) {
                is NetworkResponse.Error -> throw EventTransportException(
                    "Failed to fetch pending legacy notifications",
                    response.kException,
                )
                is NetworkResponse.Success -> response.value
            }
            page.notifications.forEach { event ->
                val size = event.payload?.length ?: 0
                if (pending.size >= MAX_PENDING_EVENTS || size > MAX_PENDING_PAYLOAD_CHARACTERS - payloadCharacters) {
                    throw EventTransportException("Legacy notification catch-up exceeded its bounded memory limit")
                }
                pending += event
                payloadCharacters += size
            }
            hasMore = page.hasMore
            cursor = page.notifications.lastOrNull()?.id ?: if (hasMore) {
                throw EventTransportException("The backend returned an empty non-terminal legacy notification page")
            } else {
                cursor
            }
        }
        return pending
    }

    private suspend fun nextPendingPage(
        api: NotificationApi,
        cursor: String?,
    ): NetworkResponse<NotificationResponse> = if (cursor == null) {
        api.getAllNotifications(NOTIFICATIONS_QUERY_SIZE, owner.identity.clientId)
    } else {
        api.notificationsByBatch(NOTIFICATIONS_QUERY_SIZE, owner.identity.clientId, cursor)
    }

    private fun EventResponseToStore.toEnvelope(isLive: Boolean): EventEnvelope<WireRawEvent> {
        val digest = calcSHA256(
            "${if (payload == null) NULL_PAYLOAD_MARKER else PRESENT_PAYLOAD_MARKER}\u0000${payload.orEmpty()}\u0000$transient"
                .encodeToByteArray(),
        ).toHex()
        val key = EventIdempotencyKey("$id:$digest")
        return EventEnvelope(
            key = key,
            backendEventId = id,
            cursor = EventCursor(id),
            payload = WireRawEvent(key, this),
            acknowledgement = EventAcknowledgement(
                value = (if (transient) TRANSIENT_ACK_PREFIX else PERSISTENT_ACK_PREFIX) + id,
                recovery = EventAcknowledgementRecovery.REPLAY,
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

    private fun reconnectDelayMillis(consecutiveFailures: Int): Long {
        val exponent = (consecutiveFailures - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        return (INITIAL_RECONNECT_DELAY_MILLIS shl exponent).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

    private sealed interface OpenSignal {
        data object Ready : OpenSignal
        data class Failure(val description: String, val cause: Throwable? = null) : OpenSignal
    }

    private sealed interface OpenAttempt {
        data class Started(
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
    )

    private class EventTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private companion object {
        const val NOTIFICATIONS_QUERY_SIZE = 100
        const val CLOSE_TIMEOUT_MILLIS = 2_000L
        const val INITIAL_RECONNECT_DELAY_MILLIS = 250L
        const val MAX_RECONNECT_DELAY_MILLIS = 4_000L
        const val MAX_CONSECUTIVE_RECONNECT_ATTEMPTS = 5
        const val MAX_BACKOFF_EXPONENT = 4
        const val STABLE_CONNECTION_MILLIS = 30_000L
        const val MAX_PENDING_EVENTS = 1_024
        const val MAX_PENDING_PAYLOAD_CHARACTERS = 16 * 1_024 * 1_024
        const val INITIAL_PENDING_EVENT_CAPACITY = 64
        const val PERSISTENT_ACK_PREFIX = "legacy-persistent:"
        const val TRANSIENT_ACK_PREFIX = "legacy-transient:"
        const val NULL_PAYLOAD_MARKER = 0
        const val PRESENT_PAYLOAD_MARKER = 1
        const val BYTE_MASK = 0xFF
        const val HEX_RADIX = 16
        const val HEX_BYTE_WIDTH = 2
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
