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

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class NotificationSyncScope(
    public val accountId: String,
    public val clientId: String
)

/** An opaque backend cursor. The engine never compares or orders cursor values. */
public data class NotificationSyncCursor(public val value: String)

public data class NotificationEventKey(public val serverEventId: String)

/**
 * Exact raw transport envelope owned by this value.
 *
 * Marker IDs and delivery tags intentionally are not fields of this type, preventing accidental
 * persistence of transport-session data.
 */
public class RawNotificationEvent(
    public val key: NotificationEventKey,
    rawEnvelope: ByteArray,
    public val isTransient: Boolean,
    public val cursor: NotificationSyncCursor?
) {
    private val ownedRawEnvelope: ByteArray = rawEnvelope.copyOf()

    public val rawEnvelope: ByteArray
        get() = ownedRawEnvelope.copyOf()

    override fun equals(other: Any?): Boolean =
        other is RawNotificationEvent &&
                key == other.key &&
                isTransient == other.isTransient &&
                cursor == other.cursor &&
                ownedRawEnvelope.contentEquals(other.ownedRawEnvelope)

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + isTransient.hashCode()
        result = 31 * result + (cursor?.hashCode() ?: 0)
        result = 31 * result + ownedRawEnvelope.contentHashCode()
        return result
    }
}

/** Raw event read back from the durable inbox, also with copy-owned bytes. */
public class StagedNotificationEvent(
    public val key: NotificationEventKey,
    rawEnvelope: ByteArray,
    public val isTransient: Boolean,
    public val cursor: NotificationSyncCursor?
) {
    private val ownedRawEnvelope: ByteArray = rawEnvelope.copyOf()

    public val rawEnvelope: ByteArray
        get() = ownedRawEnvelope.copyOf()
}

public data class BoundedNotificationSyncRequest(
    public val scope: NotificationSyncScope,
    public val markerId: String,
    public val absoluteDeadline: Instant,
    public val budget: NotificationSyncBudget = NotificationSyncBudget()
)

public data class NotificationSyncBudget(
    public val maxTransportFrames: Int = DEFAULT_MAX_TRANSPORT_FRAMES,
    public val maxEventsToStage: Int = DEFAULT_MAX_EVENTS_TO_STAGE,
    public val maxDrainBatches: Int = DEFAULT_MAX_DRAIN_BATCHES,
    public val maxEventsPerDrainBatch: Int = DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH,
    public val deadlineSafetyMargin: Duration = DEFAULT_DEADLINE_SAFETY_MARGIN
) {
    public companion object {
        public const val DEFAULT_MAX_TRANSPORT_FRAMES: Int = 200
        public const val DEFAULT_MAX_EVENTS_TO_STAGE: Int = 100
        public const val DEFAULT_MAX_DRAIN_BATCHES: Int = 4
        public const val DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH: Int = 25
        public val DEFAULT_DEADLINE_SAFETY_MARGIN: Duration = 2.seconds
    }
}

public data class NotificationSyncSummary(
    public val transportFramesReceived: Int,
    public val eventsInserted: Int,
    public val eventsAlreadyStaged: Int,
    public val transportAcksAcceptedByLocalWriter: Int,
    public val eventsReceiveMaterialized: Int,
    public val drainBatchesRead: Int
)

public sealed interface BoundedNotificationSyncResult {
    public val summary: NotificationSyncSummary

    public data class Complete(
        override val summary: NotificationSyncSummary
    ) : BoundedNotificationSyncResult

    public data class Partial(
        public val reason: PartialSyncReason,
        override val summary: NotificationSyncSummary
    ) : BoundedNotificationSyncResult

    public data class LockUnavailable(
        override val summary: NotificationSyncSummary
    ) : BoundedNotificationSyncResult

    public data class DeadlineReached(
        override val summary: NotificationSyncSummary
    ) : BoundedNotificationSyncResult

    public data class ForegroundRecoveryRequired(
        public val reason: ForegroundRecoveryReason,
        override val summary: NotificationSyncSummary
    ) : BoundedNotificationSyncResult

    public data class TerminalFailure(
        public val reason: TerminalSyncFailureReason,
        override val summary: NotificationSyncSummary
    ) : BoundedNotificationSyncResult
}

public enum class PartialSyncReason {
    LEASE_ACQUISITION_FAILED,
    TRANSPORT_OPEN_FAILED,
    TRANSPORT_RECEIVE_FAILED,
    TRANSPORT_CLOSED,
    TRANSPORT_ACK_REJECTED,
    STORAGE_FAILED,
    PROCESSING_FAILED,
    EVENT_BUDGET_EXHAUSTED,
    TRANSPORT_FRAME_BUDGET_EXHAUSTED,
    BATCH_BUDGET_EXHAUSTED,
    UNEXPECTED_TRANSPORT_PAYLOAD
}

public enum class ForegroundRecoveryReason {
    MISSED_NOTIFICATION,
    LEGACY_CATCH_UP_NOT_PROVEN,
    EVENT_PROCESSING_DEFERRED
}

public enum class TerminalSyncFailureReason {
    INVALID_REQUEST,
    LEASE_FAILURE,
    TRANSPORT_CONFIGURATION,
    STORAGE_CONFIGURATION,
    RAW_EVENT_INTEGRITY_CONFLICT,
    PROCESSOR_CONFIGURATION
}
