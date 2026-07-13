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

/**
 * Coordinates exclusive synchronization ownership for one account.
 *
 * Implementations must not wait for another process to release the lease. A successful handle
 * owns all native resources required by the lease and releases them from the non-suspending,
 * idempotent [NotificationSyncLease.release] call. If cancellation occurs after native acquisition
 * but before [LeaseAcquireResult.Acquired] is delivered, the implementation must release that
 * resource itself. Acquisition must be bounded and cancellation-cooperative.
 */
public fun interface NotificationSyncLeaseCoordinator {
    public suspend fun tryAcquire(scope: NotificationSyncScope): LeaseAcquireResult
}

public sealed interface LeaseAcquireResult {
    public data class Acquired(public val lease: NotificationSyncLease) : LeaseAcquireResult
    public data object Unavailable : LeaseAcquireResult
    public data object RetryableFailure : LeaseAcquireResult
    public data object TerminalFailure : LeaseAcquireResult
}

public fun interface NotificationSyncLease {
    /** Must be bounded, idempotent, and must not throw. */
    public fun release()
}

/**
 * Durable storage boundary for raw notification events.
 *
 * [stageRawEventAndAdvanceCursor] is deliberately one operation. Its implementation must insert
 * or verify the raw event and, for a non-transient event, advance the opaque cursor in the same
 * transaction, using the event's explicit opaque cursor rather than inferring one from its key. A
 * transient event is staged without changing the cursor. Returning [StageResult.Durable] certifies
 * that the raw bytes are locally durable and the cursor invariant holds, so a transport ACK may be
 * enqueued afterwards. [DurableStageStatus.ALREADY_STAGED] is valid only when the scoped key, exact
 * raw bytes, transient flag, and cursor metadata all match the existing row. Any mismatch for the
 * same scoped key must return [StageResult.Conflict] before an ACK is attempted.
 *
 * Every suspending operation must be bounded and cancellation-cooperative. Mutations must be
 * idempotent because a deadline can race a committed operation. A small atomic storage transaction
 * may finish before propagating cancellation, but it must not hide a retry or unbounded operation.
 */
public interface NotificationSyncInbox {
    public suspend fun readCursor(scope: NotificationSyncScope): InboxReadResult<NotificationSyncCursor?>

    public suspend fun stageRawEventAndAdvanceCursor(
        scope: NotificationSyncScope,
        event: RawNotificationEvent
    ): StageResult

    /**
     * Returns pending receive work in a deterministic, stable order for repeated reads.
     * Implementations must not return more than [limit] rows.
     */
    public suspend fun readPendingReceiveBatch(
        scope: NotificationSyncScope,
        limit: Int
    ): InboxReadResult<PendingReceiveBatch>

    /**
     * Records that receive-only materialization completed for this raw row.
     *
     * This transition must be idempotent. It must not delete the raw envelope or mark the row as
     * imported into the foreground application's database; foreground import is a later protocol.
     */
    public suspend fun markReceiveProcessingCompleted(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey
    ): InboxWriteResult

    /** Idempotently records a scope-wide recovery signal such as a missed-notification condition. */
    public suspend fun markGlobalForegroundRecoveryRequired(
        scope: NotificationSyncScope,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult

    /**
     * Defers one exact raw row to foreground handling in an idempotent status transition.
     * This must retain the raw envelope and must not mark foreground import complete.
     */
    public suspend fun markEventDeferredToForeground(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult
}

public sealed interface InboxReadResult<out T> {
    public data class Success<T>(public val value: T) : InboxReadResult<T>
    public data object RetryableFailure : InboxReadResult<Nothing>
    public data object TerminalFailure : InboxReadResult<Nothing>
}

public sealed interface InboxWriteResult {
    public data object Success : InboxWriteResult
    public data object RetryableFailure : InboxWriteResult
    public data object TerminalFailure : InboxWriteResult
}

public sealed interface StageResult {
    public data class Durable(public val status: DurableStageStatus) : StageResult
    public data object Conflict : StageResult
    public data object RetryableFailure : StageResult
    public data object TerminalFailure : StageResult
}

public enum class DurableStageStatus {
    INSERTED,
    ALREADY_STAGED
}

/**
 * A bounded stable-order page with explicit knowledge of whether more receive work remains.
 * [hasMore] must be computed from the same stable read snapshot as [events].
 */
public class PendingReceiveBatch(
    events: List<StagedNotificationEvent>,
    public val hasMore: Boolean
) {
    public val events: List<StagedNotificationEvent> = events.toList()
}

/**
 * Minimal transport needed by a single bounded catch-up.
 *
 * A transport adapter must resolve its mode once for the request. It must not hide an unlimited
 * buffer or retry loop behind this contract. If cancellation occurs after a native session opens
 * but before [OpenSessionResult.Opened] is delivered, the adapter must close that session itself.
 * Opening, receiving, and ACK enqueueing must be bounded and cancellation-cooperative.
 */
public fun interface NotificationSyncTransport {
    public suspend fun openSession(request: NotificationTransportSessionRequest): OpenSessionResult
}

public data class NotificationTransportSessionRequest(
    public val scope: NotificationSyncScope,
    public val cursor: NotificationSyncCursor?,
    public val markerId: String
)

public sealed interface OpenSessionResult {
    public data class Opened(public val session: NotificationSyncSession) : OpenSessionResult
    public data object RetryableFailure : OpenSessionResult
    public data object TerminalFailure : OpenSessionResult
}

public interface NotificationSyncSession {
    public val mode: NotificationTransportMode

    /** Waits for one frame. The call must remain cancellable. */
    public suspend fun receive(): NotificationTransportReceiveResult

    /**
     * Attempts to enqueue a transport ACK on the open session.
     *
     * [TransportAckResult.AcceptedByLocalWriter] transfers local responsibility to the transport:
     * the frame was handed to the socket writer, or the session guarantees that [close] cannot
     * discard it. An adapter that cannot make that guarantee must return a rejected result. Local
     * acceptance does not claim backend receipt and is unrelated to chat delivery or read receipts.
     */
    public suspend fun enqueueTransportAck(deliveryTag: ULong): TransportAckResult

    /**
     * Must be non-blocking, non-suspending, idempotent, non-throwing, safe from a cancellation
     * `finally`, and preserve responsibility for every ACK reported as locally accepted.
     */
    public fun close()
}

public enum class NotificationTransportMode {
    CONSUMABLE,
    LEGACY
}

public sealed interface NotificationTransportReceiveResult {
    public data class Received(public val frame: NotificationTransportFrame) : NotificationTransportReceiveResult
    public data object RetryableFailure : NotificationTransportReceiveResult
    public data object TerminalFailure : NotificationTransportReceiveResult
}

public sealed interface TransportAckResult {
    public data object AcceptedByLocalWriter : TransportAckResult
    public data object RejectedRetryable : TransportAckResult
    public data object RejectedTerminal : TransportAckResult
}

public sealed interface NotificationTransportFrame {
    /** The delivery tag is transport-only and must never be included in the staged event. */
    public data class Event(
        public val event: RawNotificationEvent,
        public val deliveryTag: ULong?
    ) : NotificationTransportFrame

    /** Marker IDs and their delivery tags are transport-only and never advance the cursor. */
    public data class SynchronizationMarker(
        public val markerId: String,
        public val deliveryTag: ULong?
    ) : NotificationTransportFrame

    public data object MissedNotification : NotificationTransportFrame
    public data object Closed : NotificationTransportFrame
    public data object UnexpectedPayload : NotificationTransportFrame
}

/**
 * Performs receive-only processing of an already durable raw event.
 *
 * A [StagedEventProcessingResult.DurablyMaterialized] result certifies that any child/decrypted
 * receive output has been committed idempotently. It does not mean foreground import completed.
 * Processing must be bounded and cancellation-cooperative. A small atomic CoreCrypto or storage
 * commit may complete before cancellation propagates, so retrying the same event must be safe.
 */
public fun interface StagedNotificationEventProcessor {
    public suspend fun process(event: StagedNotificationEvent): StagedEventProcessingResult
}

public sealed interface StagedEventProcessingResult {
    public data object DurablyMaterialized : StagedEventProcessingResult
    public data class ForegroundRequired(public val reason: ForegroundRecoveryReason) : StagedEventProcessingResult
    public data object RetryableFailure : StagedEventProcessingResult
    public data object TerminalFailure : StagedEventProcessingResult
}
