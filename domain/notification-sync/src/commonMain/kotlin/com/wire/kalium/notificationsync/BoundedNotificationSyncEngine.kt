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

package com.wire.kalium.notificationsync

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Provides wall-clock time only for comparison with the caller-owned absolute deadline. */
public fun interface NotificationSyncClock {
    public fun now(): Instant
}

public class SystemNotificationSyncClock : NotificationSyncClock {
    override fun now(): Instant = Clock.System.now()
}

/**
 * Executes one finite consumable-notification catch-up and drains a bounded amount of durable work.
 *
 * The engine has no retry loop. External cancellation is propagated; the non-suspending session
 * close and lease release contracts are always invoked from `finally`.
 */
public class BoundedNotificationSyncEngine(
    private val leaseCoordinator: NotificationSyncLeaseCoordinator,
    private val inbox: NotificationSyncInbox,
    private val transport: NotificationSyncTransport,
    private val eventProcessor: StagedNotificationEventProcessor,
    private val clock: NotificationSyncClock = SystemNotificationSyncClock()
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    public suspend fun syncOnce(request: BoundedNotificationSyncRequest): BoundedNotificationSyncResult {
        val stats = MutableSyncStats()
        validate(request)?.let { return it.withSummary(stats.snapshot()) }

        var lease: NotificationSyncLease? = null
        var session: NotificationSyncSession? = null
        try {
            when (
                val leaseCall = beforeDeadline(request) {
                    leaseCoordinator.tryAcquire(request.scope).also { result ->
                        if (result is LeaseAcquireResult.Acquired) lease = result.lease
                    }
                }
            ) {
                DeadlineCall.Expired -> return deadline(stats)
                is DeadlineCall.Completed -> when (val result = leaseCall.value) {
                    is LeaseAcquireResult.Acquired -> Unit
                    LeaseAcquireResult.Unavailable -> return BoundedNotificationSyncResult.LockUnavailable(stats.snapshot())
                    LeaseAcquireResult.RetryableFailure ->
                        return partial(PartialSyncReason.LEASE_ACQUISITION_FAILED, stats)

                    LeaseAcquireResult.TerminalFailure ->
                        return terminal(TerminalSyncFailureReason.LEASE_FAILURE, stats)
                }
            }

            val cursor = when (val cursorCall = beforeDeadline(request) { inbox.readCursor(request.scope) }) {
                DeadlineCall.Expired -> return deadline(stats)
                is DeadlineCall.Completed -> when (val result = cursorCall.value) {
                    is InboxReadResult.Success -> result.value
                    InboxReadResult.RetryableFailure -> return partial(PartialSyncReason.STORAGE_FAILED, stats)
                    InboxReadResult.TerminalFailure -> return terminal(TerminalSyncFailureReason.STORAGE_CONFIGURATION, stats)
                }
            }

            when (
                val openCall = beforeDeadline(request) {
                    transport.openSession(
                        NotificationTransportSessionRequest(request.scope, cursor, request.markerId)
                    ).also { result ->
                        if (result is OpenSessionResult.Opened) session = result.session
                    }
                }
            ) {
                DeadlineCall.Expired -> return deadline(stats)
                is DeadlineCall.Completed -> when (val result = openCall.value) {
                    is OpenSessionResult.Opened -> Unit
                    OpenSessionResult.RetryableFailure -> return partial(PartialSyncReason.TRANSPORT_OPEN_FAILED, stats)
                    OpenSessionResult.TerminalFailure -> return terminal(TerminalSyncFailureReason.TRANSPORT_CONFIGURATION, stats)
                }
            }

            val activeSession = session
                ?: return terminal(TerminalSyncFailureReason.TRANSPORT_CONFIGURATION, stats)
            if (activeSession.mode == NotificationTransportMode.LEGACY) {
                return foregroundRecovery(
                    request,
                    ForegroundRecoveryReason.LEGACY_CATCH_UP_NOT_PROVEN,
                    stats
                )
            }

            val catchUpResult = catchUp(request, activeSession, stats)
            if (catchUpResult != null) return catchUpResult

            activeSession.close()
            session = null
            return drainInbox(request, stats)
        } finally {
            try {
                session?.close()
            } finally {
                lease?.release()
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "ReturnCount")
    private suspend fun catchUp(
        request: BoundedNotificationSyncRequest,
        session: NotificationSyncSession,
        stats: MutableSyncStats
    ): BoundedNotificationSyncResult? {
        var eventCount = 0
        while (true) {
            if (stats.transportFramesReceived >= request.budget.maxTransportFrames) {
                return partial(PartialSyncReason.TRANSPORT_FRAME_BUDGET_EXHAUSTED, stats)
            }
            val frame = when (val receiveCall = beforeDeadline(request) { session.receive() }) {
                DeadlineCall.Expired -> return deadline(stats)
                is DeadlineCall.Completed -> when (val result = receiveCall.value) {
                    is NotificationTransportReceiveResult.Received -> result.frame
                    NotificationTransportReceiveResult.RetryableFailure ->
                        return partial(PartialSyncReason.TRANSPORT_RECEIVE_FAILED, stats)

                    NotificationTransportReceiveResult.TerminalFailure ->
                        return terminal(TerminalSyncFailureReason.TRANSPORT_CONFIGURATION, stats)
                }
            }
            stats.transportFramesReceived++

            when (frame) {
                is NotificationTransportFrame.Event -> {
                    if (eventCount >= request.budget.maxEventsToStage) {
                        return partial(PartialSyncReason.EVENT_BUDGET_EXHAUSTED, stats)
                    }
                    if (!frame.event.isTransient && frame.event.cursor == null) {
                        return terminal(TerminalSyncFailureReason.TRANSPORT_CONFIGURATION, stats)
                    }
                    eventCount++
                    when (
                        val stageCall = beforeDeadline(request) {
                            inbox.stageRawEventAndAdvanceCursor(request.scope, frame.event)
                        }
                    ) {
                        DeadlineCall.Expired -> return deadline(stats)
                        is DeadlineCall.Completed -> when (val result = stageCall.value) {
                            is StageResult.Durable -> when (result.status) {
                                DurableStageStatus.INSERTED -> stats.eventsInserted++
                                DurableStageStatus.ALREADY_STAGED -> stats.eventsAlreadyStaged++
                            }

                            StageResult.Conflict ->
                                return terminal(TerminalSyncFailureReason.RAW_EVENT_INTEGRITY_CONFLICT, stats)
                            StageResult.RetryableFailure -> return partial(PartialSyncReason.STORAGE_FAILED, stats)
                            StageResult.TerminalFailure ->
                                return terminal(TerminalSyncFailureReason.STORAGE_CONFIGURATION, stats)
                        }
                    }
                    frame.deliveryTag?.let { deliveryTag ->
                        acknowledge(request, session, deliveryTag, stats)?.let { return it }
                    }
                }

                is NotificationTransportFrame.SynchronizationMarker -> {
                    if (frame.markerId != request.markerId) continue
                    frame.deliveryTag?.let { deliveryTag ->
                        acknowledge(request, session, deliveryTag, stats)?.let { return it }
                    }
                    return null
                }

                NotificationTransportFrame.MissedNotification ->
                    return foregroundRecovery(request, ForegroundRecoveryReason.MISSED_NOTIFICATION, stats)

                NotificationTransportFrame.Closed -> return partial(PartialSyncReason.TRANSPORT_CLOSED, stats)
                NotificationTransportFrame.UnexpectedPayload ->
                    return partial(PartialSyncReason.UNEXPECTED_TRANSPORT_PAYLOAD, stats)
            }
        }
    }

    private suspend fun acknowledge(
        request: BoundedNotificationSyncRequest,
        session: NotificationSyncSession,
        deliveryTag: ULong,
        stats: MutableSyncStats
    ): BoundedNotificationSyncResult? =
        when (val ackCall = beforeDeadline(request) { session.enqueueTransportAck(deliveryTag) }) {
            DeadlineCall.Expired -> deadline(stats)
            is DeadlineCall.Completed -> when (ackCall.value) {
                TransportAckResult.AcceptedByLocalWriter -> {
                    stats.transportAcksAcceptedByLocalWriter++
                    null
                }

                TransportAckResult.RejectedRetryable -> partial(PartialSyncReason.TRANSPORT_ACK_REJECTED, stats)
                TransportAckResult.RejectedTerminal -> terminal(TerminalSyncFailureReason.TRANSPORT_CONFIGURATION, stats)
            }
        }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun drainInbox(
        request: BoundedNotificationSyncRequest,
        stats: MutableSyncStats
    ): BoundedNotificationSyncResult {
        repeat(request.budget.maxDrainBatches) {
            val batch = when (
                val readCall = beforeDeadline(request) {
                    inbox.readPendingReceiveBatch(request.scope, request.budget.maxEventsPerDrainBatch)
                }
            ) {
                DeadlineCall.Expired -> return deadline(stats)
                is DeadlineCall.Completed -> when (val result = readCall.value) {
                    is InboxReadResult.Success -> result.value
                    InboxReadResult.RetryableFailure -> return partial(PartialSyncReason.STORAGE_FAILED, stats)
                    InboxReadResult.TerminalFailure -> return terminal(TerminalSyncFailureReason.STORAGE_CONFIGURATION, stats)
                }
            }
            stats.drainBatchesRead++
            if (batch.events.size > request.budget.maxEventsPerDrainBatch || batch.events.isEmpty() && batch.hasMore) {
                return terminal(TerminalSyncFailureReason.STORAGE_CONFIGURATION, stats)
            }
            if (batch.events.isEmpty()) return BoundedNotificationSyncResult.Complete(stats.snapshot())

            for (event in batch.events) {
                when (val processCall = beforeDeadline(request) { eventProcessor.process(event) }) {
                    DeadlineCall.Expired -> return deadline(stats)
                    is DeadlineCall.Completed -> when (val result = processCall.value) {
                        StagedEventProcessingResult.DurablyMaterialized -> Unit
                        is StagedEventProcessingResult.ForegroundRequired ->
                            return deferEventToForeground(request, event.key, result.reason, stats)

                        StagedEventProcessingResult.RetryableFailure ->
                            return partial(PartialSyncReason.PROCESSING_FAILED, stats)

                        StagedEventProcessingResult.TerminalFailure ->
                            return terminal(TerminalSyncFailureReason.PROCESSOR_CONFIGURATION, stats)
                    }
                }

                when (
                    val markCall = beforeDeadline(request) {
                        inbox.markReceiveProcessingCompleted(request.scope, event.key)
                    }
                ) {
                    DeadlineCall.Expired -> return deadline(stats)
                    is DeadlineCall.Completed -> when (markCall.value) {
                        InboxWriteResult.Success -> stats.eventsReceiveMaterialized++
                        InboxWriteResult.RetryableFailure -> return partial(PartialSyncReason.STORAGE_FAILED, stats)
                        InboxWriteResult.TerminalFailure ->
                            return terminal(TerminalSyncFailureReason.STORAGE_CONFIGURATION, stats)
                    }
                }
            }
            if (!batch.hasMore) {
                return BoundedNotificationSyncResult.Complete(stats.snapshot())
            }
        }
        return partial(PartialSyncReason.BATCH_BUDGET_EXHAUSTED, stats)
    }

    private suspend fun foregroundRecovery(
        request: BoundedNotificationSyncRequest,
        reason: ForegroundRecoveryReason,
        stats: MutableSyncStats
    ): BoundedNotificationSyncResult =
        when (
            val markCall = beforeDeadline(request) {
                inbox.markGlobalForegroundRecoveryRequired(request.scope, reason)
            }
        ) {
            DeadlineCall.Expired -> deadline(stats)
            is DeadlineCall.Completed -> when (markCall.value) {
                InboxWriteResult.Success -> BoundedNotificationSyncResult.ForegroundRecoveryRequired(reason, stats.snapshot())
                InboxWriteResult.RetryableFailure -> partial(PartialSyncReason.STORAGE_FAILED, stats)
                InboxWriteResult.TerminalFailure -> terminal(TerminalSyncFailureReason.STORAGE_CONFIGURATION, stats)
            }
        }

    private suspend fun deferEventToForeground(
        request: BoundedNotificationSyncRequest,
        eventKey: NotificationEventKey,
        reason: ForegroundRecoveryReason,
        stats: MutableSyncStats
    ): BoundedNotificationSyncResult =
        when (
            val markCall = beforeDeadline(request) {
                inbox.markEventDeferredToForeground(request.scope, eventKey, reason)
            }
        ) {
            DeadlineCall.Expired -> deadline(stats)
            is DeadlineCall.Completed -> when (markCall.value) {
                InboxWriteResult.Success -> BoundedNotificationSyncResult.ForegroundRecoveryRequired(reason, stats.snapshot())
                InboxWriteResult.RetryableFailure -> partial(PartialSyncReason.STORAGE_FAILED, stats)
                InboxWriteResult.TerminalFailure -> terminal(TerminalSyncFailureReason.STORAGE_CONFIGURATION, stats)
            }
        }

    private fun validate(request: BoundedNotificationSyncRequest): TerminalSyncFailureReason? {
        val budget = request.budget
        return TerminalSyncFailureReason.INVALID_REQUEST.takeIf {
            request.scope.accountId.isBlank() ||
                    request.scope.clientId.isBlank() ||
                    request.markerId.isBlank() ||
                    budget.maxTransportFrames <= 0 ||
                    budget.maxEventsToStage <= 0 ||
                    budget.maxDrainBatches <= 0 ||
                    budget.maxEventsPerDrainBatch <= 0 ||
                    budget.deadlineSafetyMargin < Duration.ZERO
        }
    }

    private suspend fun <T> beforeDeadline(
        request: BoundedNotificationSyncRequest,
        block: suspend () -> T
    ): DeadlineCall<T> {
        val remaining = request.absoluteDeadline - clock.now() - request.budget.deadlineSafetyMargin
        val timeoutMillis = remaining.inWholeMilliseconds
        if (timeoutMillis <= 0) return DeadlineCall.Expired
        return withTimeoutOrNull(timeoutMillis.milliseconds) {
            DeadlineCall.Completed(block())
        } ?: DeadlineCall.Expired
    }

    private fun TerminalSyncFailureReason.withSummary(summary: NotificationSyncSummary): BoundedNotificationSyncResult =
        BoundedNotificationSyncResult.TerminalFailure(this, summary)

    private fun deadline(stats: MutableSyncStats): BoundedNotificationSyncResult =
        BoundedNotificationSyncResult.DeadlineReached(stats.snapshot())

    private fun partial(reason: PartialSyncReason, stats: MutableSyncStats): BoundedNotificationSyncResult =
        BoundedNotificationSyncResult.Partial(reason, stats.snapshot())

    private fun terminal(reason: TerminalSyncFailureReason, stats: MutableSyncStats): BoundedNotificationSyncResult =
        BoundedNotificationSyncResult.TerminalFailure(reason, stats.snapshot())
}

private sealed interface DeadlineCall<out T> {
    data class Completed<T>(val value: T) : DeadlineCall<T>
    data object Expired : DeadlineCall<Nothing>
}

private class MutableSyncStats {
    var transportFramesReceived: Int = 0
    var eventsInserted: Int = 0
    var eventsAlreadyStaged: Int = 0
    var transportAcksAcceptedByLocalWriter: Int = 0
    var eventsReceiveMaterialized: Int = 0
    var drainBatchesRead: Int = 0

    fun snapshot(): NotificationSyncSummary = NotificationSyncSummary(
        transportFramesReceived = transportFramesReceived,
        eventsInserted = eventsInserted,
        eventsAlreadyStaged = eventsAlreadyStaged,
        transportAcksAcceptedByLocalWriter = transportAcksAcceptedByLocalWriter,
        eventsReceiveMaterialized = eventsReceiveMaterialized,
        drainBatchesRead = drainBatchesRead
    )
}
