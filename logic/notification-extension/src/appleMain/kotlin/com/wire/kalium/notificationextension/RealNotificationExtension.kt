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

@file:OptIn(
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    com.wire.kalium.util.InternalKaliumApi::class
)
@file:Suppress("LongParameterList", "TooManyFunctions")

package com.wire.kalium.notificationextension

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.notificationextension.NotificationExtensionCoreLogic
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicBridge
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicContentKind
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicMessage
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicProtocol
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicReceiveStatus
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportAckStatus
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportFrame
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportMode
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportOpenStatus
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportReceiveStatus
import com.wire.kalium.notificationsync.BoundedNotificationSyncEngine
import com.wire.kalium.notificationsync.BoundedNotificationSyncRequest
import com.wire.kalium.notificationsync.DurableStageStatus
import com.wire.kalium.notificationsync.ForegroundRecoveryReason
import com.wire.kalium.notificationsync.InboxReadResult
import com.wire.kalium.notificationsync.InboxWriteResult
import com.wire.kalium.notificationsync.NotificationEventKey
import com.wire.kalium.notificationsync.NotificationSyncBudget
import com.wire.kalium.notificationsync.NotificationSyncCursor
import com.wire.kalium.notificationsync.NotificationSyncInbox
import com.wire.kalium.notificationsync.NotificationSyncScope
import com.wire.kalium.notificationsync.NotificationSyncSession
import com.wire.kalium.notificationsync.NotificationSyncTransport
import com.wire.kalium.notificationsync.NotificationTransportFrame
import com.wire.kalium.notificationsync.NotificationTransportMode
import com.wire.kalium.notificationsync.NotificationTransportReceiveResult
import com.wire.kalium.notificationsync.NotificationTransportSessionRequest
import com.wire.kalium.notificationsync.OpenSessionResult
import com.wire.kalium.notificationsync.PendingReceiveBatch
import com.wire.kalium.notificationsync.RawNotificationEvent
import com.wire.kalium.notificationsync.StageResult
import com.wire.kalium.notificationsync.StagedEventProcessingResult
import com.wire.kalium.notificationsync.StagedNotificationEvent
import com.wire.kalium.notificationsync.StagedNotificationEventProcessor
import com.wire.kalium.notificationsync.TransportAckResult
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * Shared paths and keychain identity already used by the logged-in host app.
 *
 * [kaliumRootPath] must point at the same App Group-backed Kalium directory as the app. The app and
 * NSE targets must also have matching App Group and Keychain Sharing entitlements.
 */
public data class RealNotificationExtensionConfiguration(
    public val kaliumRootPath: String,
    public val sharedAppGroupRoot: String,
    public val keychainServiceName: String,
    public val keychainAccessGroup: String,
    public val userAgent: String
) {
    override fun toString(): String = "RealNotificationExtensionConfiguration(redacted)"
}

/** Pass only the account that the incoming push belongs to; the client ID is resolved by Kalium. */
@Suppress("LongParameterList")
public data class RealNotificationExtensionRequest(
    public val userId: String,
    public val userDomain: String,
    public val absoluteDeadlineEpochMillis: Long,
    public val maxTransportFrames: Int = NotificationExtensionRequest.DEFAULT_MAX_TRANSPORT_FRAMES,
    public val maxEventsToStage: Int = NotificationExtensionRequest.DEFAULT_MAX_EVENTS_TO_STAGE,
    public val maxDrainBatches: Int = NotificationExtensionRequest.DEFAULT_MAX_DRAIN_BATCHES,
    public val maxEventsPerDrainBatch: Int = NotificationExtensionRequest.DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH,
    public val maxRawEnvelopeBytes: Int = NotificationExtensionRequest.DEFAULT_MAX_RAW_ENVELOPE_BYTES,
    public val maxRawEnvelopeBytesPerRun: Long = NotificationExtensionRequest.DEFAULT_MAX_RAW_ENVELOPE_BYTES_PER_RUN,
    public val maxDrainRawEnvelopeBytesPerRun: Long =
        NotificationExtensionRequest.DEFAULT_MAX_DRAIN_RAW_ENVELOPE_BYTES_PER_RUN,
    public val deadlineSafetyMarginMillis: Long = NotificationExtensionRequest.DEFAULT_DEADLINE_SAFETY_MARGIN_MILLIS,
    public val maxRunDurationMillis: Long = NotificationExtensionRequest.DEFAULT_MAX_RUN_DURATION_MILLIS
) {
    /** Swift-friendly constructor using the bounded engine defaults. */
    public constructor(
        userId: String,
        userDomain: String,
        absoluteDeadlineEpochMillis: Long
    ) : this(
        userId = userId,
        userDomain = userDomain,
        absoluteDeadlineEpochMillis = absoluteDeadlineEpochMillis,
        maxTransportFrames = NotificationExtensionRequest.DEFAULT_MAX_TRANSPORT_FRAMES,
        maxEventsToStage = NotificationExtensionRequest.DEFAULT_MAX_EVENTS_TO_STAGE,
        maxDrainBatches = NotificationExtensionRequest.DEFAULT_MAX_DRAIN_BATCHES,
        maxEventsPerDrainBatch = NotificationExtensionRequest.DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH,
        maxRawEnvelopeBytes = NotificationExtensionRequest.DEFAULT_MAX_RAW_ENVELOPE_BYTES,
        maxRawEnvelopeBytesPerRun = NotificationExtensionRequest.DEFAULT_MAX_RAW_ENVELOPE_BYTES_PER_RUN,
        maxDrainRawEnvelopeBytesPerRun = NotificationExtensionRequest.DEFAULT_MAX_DRAIN_RAW_ENVELOPE_BYTES_PER_RUN,
        deadlineSafetyMarginMillis = NotificationExtensionRequest.DEFAULT_DEADLINE_SAFETY_MARGIN_MILLIS,
        maxRunDurationMillis = NotificationExtensionRequest.DEFAULT_MAX_RUN_DURATION_MILLIS
    )

    override fun toString(): String = "RealNotificationExtensionRequest(redacted)"
}

public enum class RealNotificationKind {
    TEXT,
    ASSET,
    MULTIPART,
    EDIT,
    DELETE,
    REACTION,
    CALLING,
    KNOCK,
    LOCATION
}

public enum class RealNotificationProtocol {
    PROTEUS,
    MLS
}

/** A decrypted, policy-unfiltered candidate. The host app remains responsible for presentation. */
@Suppress("LongParameterList")
public data class RealNotification(
    public val messageId: String,
    public val kind: RealNotificationKind,
    public val body: String?,
    public val mentionsSelf: Boolean,
    public val conversationId: String,
    public val conversationDomain: String,
    public val senderId: String,
    public val senderDomain: String,
    public val senderClientId: String?,
    public val timestampEpochMillis: Long,
    public val protocol: RealNotificationProtocol,
    public val legalHoldStatus: String,
    public val expiresAfterMillis: Long?
) {
    override fun toString(): String = "RealNotification(redacted)"
}

/**
 * Result of one real bounded attempt. [notifications] shows exactly what the receive-only extractor
 * produced; it is intentionally not a final iOS notification policy decision.
 */
public class RealNotificationExtensionResult internal constructor(
    public val status: NotificationExtensionStatus,
    public val reason: NotificationExtensionReason,
    public val summary: NotificationExtensionSummary,
    public val clientId: String?,
    public val markerId: String?,
    notifications: List<RealNotification>
) {
    public val notifications: List<RealNotification> = notifications.toList()

    /** No policy snapshot is available in this spike, so a production UI must still fail closed. */
    public val shouldUsePrivacyPreservingFallback: Boolean = true
}

public fun interface RealNotificationExtensionCompletion {
    public fun complete(result: RealNotificationExtensionResult)
}

public class RealNotificationExtensionRunHandle internal constructor(
    private val job: Job,
    private val cancellationKind: AtomicInt
) {
    public fun cancel() {
        cancellationKind.compareAndSet(REAL_NOT_CANCELLED, REAL_CANCELLED_BY_HOST)
        job.cancel()
    }

    public fun cancelForExpiration() {
        cancellationKind.compareAndSet(REAL_NOT_CANCELLED, REAL_CANCELLED_FOR_EXPIRATION)
        job.cancel()
    }
}

/**
 * Testable real-account spike entry point.
 *
 * The transport is intentionally not ACKed because this assembly uses a volatile per-invocation
 * inbox: a backend redelivery is safer than claiming durability before the encrypted App Group
 * handoff store is available. CoreCrypto and protobuf decoding are real; notification policy and
 * foreground import remain outside this component.
 */
public class RealNotificationExtension(
    private val configuration: RealNotificationExtensionConfiguration
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val coreLogic: NotificationExtensionCoreLogic by lazy {
        NotificationExtensionCoreLogic(
            rootPath = configuration.kaliumRootPath,
            keychainConfig = ApplePersistenceConfig(
                serviceName = configuration.keychainServiceName,
                accessGroup = configuration.keychainAccessGroup
            ),
            kaliumConfigs = KaliumConfigs(enableCalling = false),
            userAgent = configuration.userAgent
        )
    }

    public fun begin(
        request: RealNotificationExtensionRequest,
        completion: RealNotificationExtensionCompletion
    ): RealNotificationExtensionRunHandle {
        val cancellationKind = AtomicInt(REAL_NOT_CANCELLED)
        val completionGate = RealCompletionGate(completion)
        val job = scope.launch {
            val result = try {
                execute(request)
            } catch (_: CancellationException) {
                cancelledResult(cancellationKind.load())
            } catch (_: Throwable) {
                unavailableResult(NotificationExtensionReason.RUNTIME_FAILURE)
            }
            completionGate.complete(result)
        }
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                completionGate.complete(cancelledResult(cancellationKind.load()))
            }
        }
        return RealNotificationExtensionRunHandle(job, cancellationKind)
    }

    public fun close() {
        scope.cancel()
    }

    @Suppress("ReturnCount")
    private suspend fun execute(request: RealNotificationExtensionRequest): RealNotificationExtensionResult {
        if (!request.isValid() || !configuration.isValid()) {
            return unavailableResult(NotificationExtensionReason.INVALID_REQUEST)
        }
        val userId = UserId(request.userId, request.userDomain)
        val bridge = coreLogic.createBridge(userId)
        return try {
            val clientId = bridge.resolveClientId()
                ?: return unavailableResult(NotificationExtensionReason.TRANSPORT_CONFIGURATION)
            val markerId = Uuid.random().toString()
            val inbox = VolatileNotificationSyncInbox()
            val candidates = LinkedHashMap<String, RealNotification>()
            val engine = BoundedNotificationSyncEngine(
                leaseCoordinator = AppleNotificationSyncLeaseCoordinator(
                    configuration.sharedAppGroupRoot,
                    closeAttemptResources = bridge::close
                ),
                inbox = inbox,
                transport = RealLogicNotificationTransport(bridge),
                eventProcessor = RealLogicEventProcessor(bridge, candidates)
            )
            val domainResult = engine.syncOnce(
                BoundedNotificationSyncRequest(
                    scope = NotificationSyncScope(accountId = userId.toString(), clientId = clientId),
                    markerId = markerId,
                    absoluteDeadline = Instant.fromEpochMilliseconds(request.absoluteDeadlineEpochMillis),
                    budget = request.toBudget()
                )
            )
            val base = domainResult.toExtensionResult()
            RealNotificationExtensionResult(
                status = base.status,
                reason = base.reason,
                summary = base.summary,
                clientId = clientId,
                markerId = markerId,
                notifications = candidates.values.toList()
            )
        } finally {
            bridge.close()
        }
    }
}

private class RealCompletionGate(
    private val completion: RealNotificationExtensionCompletion
) {
    private val state = AtomicInt(REAL_COMPLETION_PENDING)

    fun complete(result: RealNotificationExtensionResult) {
        if (!state.compareAndSet(REAL_COMPLETION_PENDING, REAL_COMPLETION_DELIVERED)) return
        runCatching { completion.complete(result) }
    }
}

private class RealLogicNotificationTransport(
    private val bridge: NotificationExtensionLogicBridge
) : NotificationSyncTransport {
    override suspend fun openSession(request: NotificationTransportSessionRequest): OpenSessionResult {
        val opened = bridge.openTransport(request.scope.clientId, request.markerId)
        return when (opened.status) {
            NotificationExtensionLogicTransportOpenStatus.OPENED -> {
                val session = opened.session
                if (session == null) OpenSessionResult.TerminalFailure else OpenSessionResult.Opened(
                    RealLogicNotificationSession(session)
                )
            }

            NotificationExtensionLogicTransportOpenStatus.RETRYABLE_FAILURE -> OpenSessionResult.RetryableFailure
            NotificationExtensionLogicTransportOpenStatus.TERMINAL_FAILURE -> OpenSessionResult.TerminalFailure
        }
    }
}

private class RealLogicNotificationSession(
    private val delegate: com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportSession
) : NotificationSyncSession {
    override val mode: NotificationTransportMode = when (delegate.mode) {
        NotificationExtensionLogicTransportMode.CONSUMABLE -> NotificationTransportMode.CONSUMABLE
        NotificationExtensionLogicTransportMode.LEGACY -> NotificationTransportMode.LEGACY
    }

    override suspend fun receive(): NotificationTransportReceiveResult {
        val result = delegate.receive()
        return when (result.status) {
            NotificationExtensionLogicTransportReceiveStatus.RECEIVED -> result.frame?.toDomainFrame()?.let {
                NotificationTransportReceiveResult.Received(it)
            } ?: NotificationTransportReceiveResult.TerminalFailure

            NotificationExtensionLogicTransportReceiveStatus.RETRYABLE_FAILURE ->
                NotificationTransportReceiveResult.RetryableFailure

            NotificationExtensionLogicTransportReceiveStatus.TERMINAL_FAILURE ->
                NotificationTransportReceiveResult.TerminalFailure
        }
    }

    override suspend fun enqueueTransportAck(deliveryTag: ULong): TransportAckResult =
        when (delegate.acknowledge(deliveryTag)) {
            NotificationExtensionLogicTransportAckStatus.ACCEPTED_BY_LOCAL_WRITER ->
                TransportAckResult.AcceptedByLocalWriter

            NotificationExtensionLogicTransportAckStatus.REJECTED_RETRYABLE -> TransportAckResult.RejectedRetryable
            NotificationExtensionLogicTransportAckStatus.REJECTED_TERMINAL -> TransportAckResult.RejectedTerminal
        }

    override fun close() {
        delegate.close()
    }
}

private class RealLogicEventProcessor(
    private val bridge: NotificationExtensionLogicBridge,
    private val candidates: MutableMap<String, RealNotification>
) : StagedNotificationEventProcessor {
    override suspend fun process(event: StagedNotificationEvent): StagedEventProcessingResult {
        val result = bridge.receive(event.rawEnvelope)
        result.messages.forEach { message ->
            message.toRealNotification()?.let { notification ->
                candidates[message.idempotencyKey()] = notification
            }
        }
        return when (result.status) {
            NotificationExtensionLogicReceiveStatus.MATERIALIZED -> StagedEventProcessingResult.DurablyMaterialized
            NotificationExtensionLogicReceiveStatus.FOREGROUND_REQUIRED ->
                StagedEventProcessingResult.ForegroundRequired(ForegroundRecoveryReason.EVENT_PROCESSING_DEFERRED)

            NotificationExtensionLogicReceiveStatus.RETRYABLE_FAILURE -> StagedEventProcessingResult.RetryableFailure
            NotificationExtensionLogicReceiveStatus.TERMINAL_FAILURE -> StagedEventProcessingResult.TerminalFailure
        }
    }
}

/**
 * Per-invocation staging used only while ACKs are suppressed. It preserves engine ordering and
 * duplicate checks without ever writing real plaintext notification data to disk.
 */
private class VolatileNotificationSyncInbox : NotificationSyncInbox {
    private val rows = LinkedHashMap<NotificationEventKey, VolatileRow>()
    private var cursor: NotificationSyncCursor? = null

    override suspend fun readCursor(scope: NotificationSyncScope): InboxReadResult<NotificationSyncCursor?> =
        InboxReadResult.Success(cursor)

    override suspend fun stageRawEventAndAdvanceCursor(
        scope: NotificationSyncScope,
        event: RawNotificationEvent
    ): StageResult {
        val existing = rows[event.key]
        if (existing != null) {
            return if (existing.event == event) {
                StageResult.Durable(DurableStageStatus.ALREADY_STAGED)
            } else {
                StageResult.Conflict
            }
        }
        rows[event.key] = VolatileRow(event, VolatileRowState.PENDING)
        if (!event.isTransient) cursor = event.cursor
        return StageResult.Durable(DurableStageStatus.INSERTED)
    }

    override suspend fun readPendingReceiveBatch(
        scope: NotificationSyncScope,
        limit: Int
    ): InboxReadResult<PendingReceiveBatch> {
        val pending = rows.values.filter { it.state == VolatileRowState.PENDING }
        return InboxReadResult.Success(
            PendingReceiveBatch(
                events = pending.take(limit).map { row ->
                    StagedNotificationEvent(
                        key = row.event.key,
                        rawEnvelope = row.event.rawEnvelope,
                        isTransient = row.event.isTransient,
                        cursor = row.event.cursor
                    )
                },
                hasMore = pending.size > limit
            )
        )
    }

    override suspend fun markReceiveProcessingCompleted(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey
    ): InboxWriteResult = update(eventKey, VolatileRowState.COMPLETED)

    override suspend fun markGlobalForegroundRecoveryRequired(
        scope: NotificationSyncScope,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult = InboxWriteResult.Success

    override suspend fun markEventDeferredToForeground(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult = update(eventKey, VolatileRowState.DEFERRED)

    private fun update(eventKey: NotificationEventKey, state: VolatileRowState): InboxWriteResult {
        val row = rows[eventKey] ?: return InboxWriteResult.TerminalFailure
        row.state = state
        return InboxWriteResult.Success
    }
}

private class VolatileRow(
    val event: RawNotificationEvent,
    var state: VolatileRowState
)

private enum class VolatileRowState {
    PENDING,
    COMPLETED,
    DEFERRED
}

private fun NotificationExtensionLogicTransportFrame.toDomainFrame(): NotificationTransportFrame = when (this) {
    is NotificationExtensionLogicTransportFrame.Event -> NotificationTransportFrame.Event(
        event = RawNotificationEvent(
            key = NotificationEventKey(eventId),
            rawEnvelope = rawEnvelope,
            isTransient = isTransient,
            cursor = cursor?.let(::NotificationSyncCursor)
        ),
        // A volatile inbox cannot certify crash durability, so the spike must never ACK.
        deliveryTag = null
    )

    is NotificationExtensionLogicTransportFrame.SynchronizationMarker ->
        NotificationTransportFrame.SynchronizationMarker(markerId, deliveryTag = null)

    NotificationExtensionLogicTransportFrame.MissedNotification -> NotificationTransportFrame.MissedNotification
    NotificationExtensionLogicTransportFrame.Closed -> NotificationTransportFrame.Closed
    NotificationExtensionLogicTransportFrame.UnexpectedPayload -> NotificationTransportFrame.UnexpectedPayload
}

private fun NotificationExtensionLogicMessage.toRealNotification(): RealNotification? {
    val extracted = candidate ?: return null
    return RealNotification(
        messageId = extracted.messageId,
        kind = extracted.kind.toRealKind(),
        body = extracted.body,
        mentionsSelf = extracted.mentionsSelf,
        conversationId = conversationId,
        conversationDomain = conversationDomain,
        senderId = senderId,
        senderDomain = senderDomain,
        senderClientId = senderClientId,
        timestampEpochMillis = timestampEpochMillis,
        protocol = when (protocol) {
            NotificationExtensionLogicProtocol.PROTEUS -> RealNotificationProtocol.PROTEUS
            NotificationExtensionLogicProtocol.MLS -> RealNotificationProtocol.MLS
        },
        legalHoldStatus = extracted.legalHoldStatus,
        expiresAfterMillis = extracted.expiresAfterMillis
    )
}

private fun NotificationExtensionLogicContentKind.toRealKind(): RealNotificationKind = when (this) {
    NotificationExtensionLogicContentKind.TEXT -> RealNotificationKind.TEXT
    NotificationExtensionLogicContentKind.ASSET -> RealNotificationKind.ASSET
    NotificationExtensionLogicContentKind.MULTIPART -> RealNotificationKind.MULTIPART
    NotificationExtensionLogicContentKind.EDIT -> RealNotificationKind.EDIT
    NotificationExtensionLogicContentKind.DELETE -> RealNotificationKind.DELETE
    NotificationExtensionLogicContentKind.REACTION -> RealNotificationKind.REACTION
    NotificationExtensionLogicContentKind.CALLING -> RealNotificationKind.CALLING
    NotificationExtensionLogicContentKind.KNOCK -> RealNotificationKind.KNOCK
    NotificationExtensionLogicContentKind.LOCATION -> RealNotificationKind.LOCATION
}

private fun NotificationExtensionLogicMessage.idempotencyKey(): String = "$eventId:$itemIndex:${candidate?.messageId.orEmpty()}"

private fun RealNotificationExtensionRequest.toBudget(): NotificationSyncBudget = NotificationSyncBudget(
    maxTransportFrames = maxTransportFrames,
    maxEventsToStage = maxEventsToStage,
    maxDrainBatches = maxDrainBatches,
    maxEventsPerDrainBatch = maxEventsPerDrainBatch,
    maxRawEnvelopeBytes = maxRawEnvelopeBytes,
    maxRawEnvelopeBytesPerRun = maxRawEnvelopeBytesPerRun,
    maxDrainRawEnvelopeBytesPerRun = maxDrainRawEnvelopeBytesPerRun,
    deadlineSafetyMargin = deadlineSafetyMarginMillis.milliseconds,
    maxRunDuration = maxRunDurationMillis.milliseconds
)

private fun RealNotificationExtensionRequest.isValid(): Boolean =
    userId.isNotBlank() && userDomain.isNotBlank() && absoluteDeadlineEpochMillis > 0L &&
            maxTransportFrames > 0 && maxEventsToStage > 0 && maxDrainBatches > 0 &&
            maxEventsPerDrainBatch > 0 && maxRawEnvelopeBytes > 0 &&
            maxRawEnvelopeBytesPerRun > 0 && maxDrainRawEnvelopeBytesPerRun > 0 &&
            deadlineSafetyMarginMillis >= 0L && maxRunDurationMillis > 0L

private fun RealNotificationExtensionConfiguration.isValid(): Boolean =
    kaliumRootPath.isNotBlank() && sharedAppGroupRoot.isNotBlank() &&
            keychainServiceName.isNotBlank() && keychainAccessGroup.isNotBlank() && userAgent.isNotBlank()

private fun unavailableResult(reason: NotificationExtensionReason): RealNotificationExtensionResult =
    RealNotificationExtensionResult(
        status = NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE,
        reason = reason,
        summary = NotificationExtensionSummary.Empty,
        clientId = null,
        markerId = null,
        notifications = emptyList()
    )

private fun cancelledResult(kind: Int): RealNotificationExtensionResult = RealNotificationExtensionResult(
    status = if (kind == REAL_CANCELLED_FOR_EXPIRATION) {
        NotificationExtensionStatus.DEADLINE_REACHED
    } else {
        NotificationExtensionStatus.CANCELLED
    },
    reason = if (kind == REAL_CANCELLED_FOR_EXPIRATION) {
        NotificationExtensionReason.DEADLINE
    } else {
        NotificationExtensionReason.HOST_CANCELLED
    },
    summary = NotificationExtensionSummary.Empty,
    clientId = null,
    markerId = null,
    notifications = emptyList()
)

private const val REAL_NOT_CANCELLED = 0
private const val REAL_CANCELLED_BY_HOST = 1
private const val REAL_CANCELLED_FOR_EXPIRATION = 2
private const val REAL_COMPLETION_PENDING = 0
private const val REAL_COMPLETION_DELIVERED = 1
