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

package com.wire.kalium.nse.feasibility

import com.wire.kalium.notificationsync.BoundedNotificationSyncEngine
import com.wire.kalium.notificationsync.BoundedNotificationSyncRequest
import com.wire.kalium.notificationsync.BoundedNotificationSyncResult
import com.wire.kalium.notificationsync.DurableStageStatus
import com.wire.kalium.notificationsync.ForegroundRecoveryReason
import com.wire.kalium.notificationsync.InboxReadResult
import com.wire.kalium.notificationsync.InboxWriteResult
import com.wire.kalium.notificationsync.LeaseAcquireResult
import com.wire.kalium.notificationsync.NotificationEventKey
import com.wire.kalium.notificationsync.NotificationSyncCursor
import com.wire.kalium.notificationsync.NotificationSyncBudget
import com.wire.kalium.notificationsync.NotificationSyncInbox
import com.wire.kalium.notificationsync.NotificationSyncLease
import com.wire.kalium.notificationsync.NotificationSyncScope
import com.wire.kalium.notificationsync.NotificationSyncSession
import com.wire.kalium.notificationsync.NotificationSyncTransport
import com.wire.kalium.notificationsync.NotificationTransportFrame
import com.wire.kalium.notificationsync.NotificationTransportMode
import com.wire.kalium.notificationsync.NotificationTransportReceiveResult
import com.wire.kalium.notificationsync.OpenSessionResult
import com.wire.kalium.notificationsync.PartialSyncReason
import com.wire.kalium.notificationsync.PendingReceiveBatch
import com.wire.kalium.notificationsync.RawNotificationEvent
import com.wire.kalium.notificationsync.StageResult
import com.wire.kalium.notificationsync.StagedEventProcessingResult
import com.wire.kalium.notificationsync.StagedNotificationEvent
import com.wire.kalium.notificationsync.TransportAckResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Local-only fake-port probe. It is executable evidence, not an automated test. */
public class BoundedNotificationSyncProbe {
    public fun run(): FeasibilityProbeResult {
        val started = TimeSource.Monotonic.markNow()
        return runCatching { runBlocking { executeProbe() } }.fold(
            onSuccess = { detail ->
                FeasibilityProbeResult(
                    gate = "bounded-notification-sync",
                    passed = true,
                    elapsedNanos = started.elapsedNow().inWholeNanoseconds,
                    detail = detail
                )
            },
            onFailure = { failure ->
                FeasibilityProbeResult(
                    gate = "bounded-notification-sync",
                    passed = false,
                    elapsedNanos = started.elapsedNow().inWholeNanoseconds,
                    detail = failure.message ?: failure.toString()
                )
            }
        )
    }

    private suspend fun executeProbe(): String {
        val timeline = mutableListOf<String>()
        val inbox = ProbeInbox(timeline)
        val rawInput = byteArrayOf(PROBE_RAW_EVENT_BYTE)
        val rawEvent = RawNotificationEvent(
            key = NotificationEventKey("probe-event"),
            rawEnvelope = rawInput,
            isTransient = false,
            cursor = NotificationSyncCursor("probe-cursor")
        )
        rawInput[0] = MUTATED_INPUT_BYTE

        val session = ProbeSession(
            timeline = timeline,
            frames = listOf(
                NotificationTransportFrame.Event(rawEvent, EVENT_DELIVERY_TAG),
                NotificationTransportFrame.SynchronizationMarker(PROBE_MARKER, MARKER_DELIVERY_TAG)
            )
        )
        val lease = ProbeLease(timeline)
        val engine = BoundedNotificationSyncEngine(
            leaseCoordinator = { LeaseAcquireResult.Acquired(lease) },
            inbox = inbox,
            transport = NotificationSyncTransport { OpenSessionResult.Opened(session) },
            eventProcessor = { event ->
                check(event.rawEnvelope.contentEquals(byteArrayOf(PROBE_RAW_EVENT_BYTE)))
                timeline += "materialize:${event.key.serverEventId}"
                StagedEventProcessingResult.DurablyMaterialized
            }
        )

        val result = engine.syncOnce(
            BoundedNotificationSyncRequest(
                scope = PROBE_SCOPE,
                markerId = PROBE_MARKER,
                absoluteDeadline = Clock.System.now() + PROBE_DEADLINE,
                budget = NotificationSyncBudget(
                    maxTransportFrames = 2,
                    maxEventsToStage = 1
                )
            )
        )
        check(result is BoundedNotificationSyncResult.Complete)
        check(result.summary.eventsInserted == 1)
        check(result.summary.transportAcksAcceptedByLocalWriter == 2)
        check(result.summary.eventsReceiveMaterialized == 1)
        check(timeline.indexOf("stage:probe-event") < timeline.indexOf("ack:$EVENT_DELIVERY_TAG"))
        check(timeline.indexOf("ack:$MARKER_DELIVERY_TAG") < timeline.indexOf("close"))
        check(timeline.last() == "release")
        check(inbox.cursor == NotificationSyncCursor("probe-cursor"))
        check(inbox.retainedRawCount == 1)
        verifyImmediateDeadline()
        verifyEventBudget()
        verifyByteBudget()
        verifyHardCeilings()

        return "complete=true; markerAfterFinalEvent=true; deadlineBeforeAcquire=true; " +
                "secondEventBudgeted=true; byteBudgetBeforeStage=true; hardCeilings=true; finite=true; " +
                "stagedBeforeAck=true; cursorAtomic=true; " +
                "rawRetained=true; localWriterAcks=2; closed=true; released=true; realNetwork=false"
    }

    private suspend fun verifyImmediateDeadline() {
        var leaseAttempted = false
        val timeline = mutableListOf<String>()
        val engine = BoundedNotificationSyncEngine(
            leaseCoordinator = {
                leaseAttempted = true
                LeaseAcquireResult.Acquired(ProbeLease(timeline))
            },
            inbox = ProbeInbox(timeline),
            transport = NotificationSyncTransport {
                OpenSessionResult.Opened(ProbeSession(timeline, emptyList()))
            },
            eventProcessor = { StagedEventProcessingResult.DurablyMaterialized }
        )
        val result = engine.syncOnce(
            BoundedNotificationSyncRequest(
                scope = PROBE_SCOPE,
                markerId = PROBE_MARKER,
                absoluteDeadline = Clock.System.now() - PROBE_DEADLINE
            )
        )
        check(result is BoundedNotificationSyncResult.DeadlineReached)
        check(!leaseAttempted)
        check(timeline.isEmpty())
    }

    private suspend fun verifyEventBudget() {
        val timeline = mutableListOf<String>()
        val inbox = ProbeInbox(timeline)
        val session = ProbeSession(
            timeline = timeline,
            frames = listOf(
                NotificationTransportFrame.Event(
                    RawNotificationEvent(
                        NotificationEventKey("budget-one"),
                        byteArrayOf(1),
                        false,
                        NotificationSyncCursor("budget-cursor-one")
                    ),
                    BUDGET_FIRST_TAG
                ),
                NotificationTransportFrame.Event(
                    RawNotificationEvent(
                        NotificationEventKey("budget-two"),
                        byteArrayOf(2),
                        false,
                        NotificationSyncCursor("budget-cursor-two")
                    ),
                    BUDGET_SECOND_TAG
                )
            )
        )
        val engine = BoundedNotificationSyncEngine(
            leaseCoordinator = { LeaseAcquireResult.Acquired(ProbeLease(timeline)) },
            inbox = inbox,
            transport = NotificationSyncTransport { OpenSessionResult.Opened(session) },
            eventProcessor = { StagedEventProcessingResult.DurablyMaterialized }
        )
        val result = engine.syncOnce(
            BoundedNotificationSyncRequest(
                scope = PROBE_SCOPE,
                markerId = PROBE_MARKER,
                absoluteDeadline = Clock.System.now() + PROBE_DEADLINE,
                budget = NotificationSyncBudget(
                    maxTransportFrames = 2,
                    maxEventsToStage = 1
                )
            )
        )
        check(
            result is BoundedNotificationSyncResult.Partial &&
                    result.reason == PartialSyncReason.EVENT_BUDGET_EXHAUSTED
        )
        check(inbox.retainedRawCount == 1)
        check(inbox.cursor == NotificationSyncCursor("budget-cursor-one"))
        check(timeline.contains("ack:$BUDGET_FIRST_TAG"))
        check(!timeline.contains("stage:budget-two"))
        check(!timeline.contains("ack:$BUDGET_SECOND_TAG"))
    }

    private suspend fun verifyByteBudget() {
        val timeline = mutableListOf<String>()
        val inbox = ProbeInbox(timeline)
        val session = ProbeSession(
            timeline,
            listOf(
                NotificationTransportFrame.Event(
                    RawNotificationEvent(
                        NotificationEventKey("byte-budget"),
                        byteArrayOf(1, 2),
                        false,
                        NotificationSyncCursor("byte-budget-cursor")
                    ),
                    BUDGET_FIRST_TAG
                )
            )
        )
        val engine = BoundedNotificationSyncEngine(
            leaseCoordinator = { LeaseAcquireResult.Acquired(ProbeLease(timeline)) },
            inbox = inbox,
            transport = NotificationSyncTransport { OpenSessionResult.Opened(session) },
            eventProcessor = { StagedEventProcessingResult.DurablyMaterialized }
        )
        val result = engine.syncOnce(
            BoundedNotificationSyncRequest(
                scope = PROBE_SCOPE,
                markerId = PROBE_MARKER,
                absoluteDeadline = Clock.System.now() + PROBE_DEADLINE,
                budget = NotificationSyncBudget(maxRawEnvelopeBytes = 1)
            )
        )
        check(
            result is BoundedNotificationSyncResult.Partial &&
                    result.reason == PartialSyncReason.EVENT_BYTE_BUDGET_EXHAUSTED &&
                    result.summary.transportRawEnvelopeBytesReceived == 2L
        )
        check(inbox.retainedRawCount == 0)
        check(timeline.none { it.startsWith("stage:") || it.startsWith("ack:") })
    }

    private suspend fun verifyHardCeilings() {
        var leaseAttempted = false
        val timeline = mutableListOf<String>()
        val engine = BoundedNotificationSyncEngine(
            leaseCoordinator = {
                leaseAttempted = true
                LeaseAcquireResult.Acquired(ProbeLease(timeline))
            },
            inbox = ProbeInbox(timeline),
            transport = NotificationSyncTransport { OpenSessionResult.Opened(ProbeSession(timeline, emptyList())) },
            eventProcessor = { StagedEventProcessingResult.DurablyMaterialized }
        )
        val result = engine.syncOnce(
            BoundedNotificationSyncRequest(
                scope = PROBE_SCOPE,
                markerId = PROBE_MARKER,
                absoluteDeadline = Clock.System.now() + PROBE_DEADLINE,
                budget = NotificationSyncBudget(maxTransportFrames = 1_001)
            )
        )
        check(result is BoundedNotificationSyncResult.TerminalFailure)
        check(!leaseAttempted)
    }
}

private class ProbeLease(private val timeline: MutableList<String>) : NotificationSyncLease {
    private var released = false

    override fun release() {
        if (released) return
        released = true
        timeline += "release"
    }
}

private class ProbeSession(
    private val timeline: MutableList<String>,
    private val frames: List<NotificationTransportFrame>
) : NotificationSyncSession {
    override val mode: NotificationTransportMode = NotificationTransportMode.CONSUMABLE
    private var frameIndex = 0
    private var closed = false

    override suspend fun receive(): NotificationTransportReceiveResult =
        NotificationTransportReceiveResult.Received(frames[frameIndex++])

    override suspend fun enqueueTransportAck(deliveryTag: ULong): TransportAckResult {
        if (deliveryTag == EVENT_DELIVERY_TAG) check(timeline.contains("stage:probe-event"))
        timeline += "ack:$deliveryTag"
        return TransportAckResult.AcceptedByLocalWriter
    }

    override fun close() {
        if (closed) return
        closed = true
        timeline += "close"
    }
}

private class ProbeInbox(private val timeline: MutableList<String>) : NotificationSyncInbox {
    private val staged = linkedMapOf<NotificationEventKey, StagedNotificationEvent>()
    private val pending = linkedSetOf<NotificationEventKey>()
    var cursor: NotificationSyncCursor? = null
        private set

    val retainedRawCount: Int
        get() = staged.size

    override suspend fun readCursor(scope: NotificationSyncScope): InboxReadResult<NotificationSyncCursor?> =
        InboxReadResult.Success(cursor)

    override suspend fun stageRawEventAndAdvanceCursor(
        scope: NotificationSyncScope,
        event: RawNotificationEvent
    ): StageResult {
        val existing = staged[event.key]
        if (existing != null) {
            return if (
                existing.isTransient == event.isTransient &&
                existing.cursor == event.cursor &&
                existing.rawEnvelope.contentEquals(event.rawEnvelope)
            ) {
                StageResult.Durable(DurableStageStatus.ALREADY_STAGED)
            } else {
                StageResult.Conflict
            }
        }
        staged[event.key] = StagedNotificationEvent(event.key, event.rawEnvelope, event.isTransient, event.cursor)
        pending += event.key
        if (!event.isTransient) cursor = checkNotNull(event.cursor)
        timeline += "stage:${event.key.serverEventId}"
        return StageResult.Durable(DurableStageStatus.INSERTED)
    }

    override suspend fun readPendingReceiveBatch(
        scope: NotificationSyncScope,
        limit: Int
    ): InboxReadResult<PendingReceiveBatch> {
        val page = pending.take(limit)
        return InboxReadResult.Success(
            PendingReceiveBatch(
                events = page.map(staged::getValue),
                hasMore = pending.size > page.size
            )
        )
    }

    override suspend fun markReceiveProcessingCompleted(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey
    ): InboxWriteResult {
        pending -= eventKey
        timeline += "receive-complete:${eventKey.serverEventId}"
        return InboxWriteResult.Success
    }

    override suspend fun markGlobalForegroundRecoveryRequired(
        scope: NotificationSyncScope,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult = InboxWriteResult.Success

    override suspend fun markEventDeferredToForeground(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult {
        pending -= eventKey
        return InboxWriteResult.Success
    }
}

private val PROBE_SCOPE = NotificationSyncScope("probe-account", "probe-client")
private const val PROBE_MARKER = "probe-marker"
private const val PROBE_RAW_EVENT_BYTE: Byte = 7
private const val MUTATED_INPUT_BYTE: Byte = 99
private const val EVENT_DELIVERY_TAG: ULong = 41u
private const val MARKER_DELIVERY_TAG: ULong = 42u
private const val BUDGET_FIRST_TAG: ULong = 51u
private const val BUDGET_SECOND_TAG: ULong = 52u
private val PROBE_DEADLINE = 10.seconds
