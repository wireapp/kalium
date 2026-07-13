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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.notificationextension

import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.messaging.receiving.DecodedMessageContent
import com.wire.kalium.messaging.receiving.MessageContentDecoder
import com.wire.kalium.messagecontent.DecodedProtobufContent
import com.wire.kalium.messagecontent.ProtobufMessageContentDecoder
import com.wire.kalium.notificationinbox.DecryptionState
import com.wire.kalium.notificationinbox.ForegroundImportState
import com.wire.kalium.notificationinbox.InboxMutationResult as StoreMutationResult
import com.wire.kalium.notificationinbox.InboxReadResult as StoreReadResult
import com.wire.kalium.notificationinbox.InboxScope
import com.wire.kalium.notificationinbox.NotificationInboxFailure
import com.wire.kalium.notificationinbox.NotificationInboxStore
import com.wire.kalium.notificationinbox.NotificationState
import com.wire.kalium.notificationinbox.NOTIFICATION_RAW_ENVELOPE_FORMAT_VERSION
import com.wire.kalium.notificationinbox.RawEnvelopeDeliverySource
import com.wire.kalium.notificationinbox.RawEventStageResult
import com.wire.kalium.notificationinbox.RawEventWrite
import com.wire.kalium.notificationinbox.ReceiveChildWrite
import com.wire.kalium.notificationinbox.ReceiveChildrenStageResult
import com.wire.kalium.notificationinbox.ReceiveChildrenWrite
import com.wire.kalium.notificationsync.BoundedNotificationSyncEngine
import com.wire.kalium.notificationsync.BoundedNotificationSyncRequest
import com.wire.kalium.notificationsync.BoundedNotificationSyncResult
import com.wire.kalium.notificationsync.DurableStageStatus
import com.wire.kalium.notificationsync.ForegroundRecoveryReason
import com.wire.kalium.notificationsync.InboxReadResult
import com.wire.kalium.notificationsync.InboxWriteResult
import com.wire.kalium.notificationsync.NotificationEventKey
import com.wire.kalium.notificationsync.NotificationSyncBudget
import com.wire.kalium.notificationsync.NotificationSyncCursor
import com.wire.kalium.notificationsync.NotificationSyncInbox
import com.wire.kalium.notificationsync.NotificationSyncScope
import com.wire.kalium.notificationsync.NotificationSyncSummary
import com.wire.kalium.notificationsync.PartialSyncReason
import com.wire.kalium.notificationsync.PendingReceiveBatch
import com.wire.kalium.notificationsync.RawNotificationEvent
import com.wire.kalium.notificationsync.StageResult
import com.wire.kalium.notificationsync.StagedEventProcessingResult
import com.wire.kalium.notificationsync.StagedNotificationEvent
import com.wire.kalium.notificationsync.StagedNotificationEventProcessor
import com.wire.kalium.notificationsync.TerminalSyncFailureReason
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Flat call-event value exported by the core framework.
 *
 * Swift must copy these scalar/string values into the separate AVS framework synchronously while
 * the staged-event processor still owns the process lock. No Kotlin object may cross between the
 * two dynamic KMP frameworks.
 */
@Suppress("LongParameterList")
public data class NotificationExtensionCallEvent(
    public val payload: String,
    public val currentTimeSeconds: Long,
    public val messageTimeSeconds: Long,
    public val conversationId: String,
    public val senderUserId: String,
    public val senderClientId: String,
    public val conversationType: Int
) {
    override fun toString(): String = "NotificationExtensionCallEvent(redacted)"
}

public enum class NotificationExtensionCallProcessingStatus {
    SUCCESS,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
}

/** Swift bridge to the separately linked notification-only AVS framework. */
public fun interface NotificationExtensionCallProcessor {
    public fun process(events: List<NotificationExtensionCallEvent>): NotificationExtensionCallProcessingStatus
}

internal fun interface NotificationExtensionEngineFactory {
    fun create(): BoundedNotificationSyncEngine
}

internal class BoundedNotificationExtensionRuntime(
    private val engineFactory: NotificationExtensionEngineFactory
) : NotificationExtensionRuntime {
    override suspend fun execute(request: NotificationExtensionRequest): NotificationExtensionResult {
        val domainRequest = request.toDomainRequest()
            ?: return invalidRequestResult()
        return engineFactory.create().syncOnce(domainRequest).toExtensionResult()
    }
}

/** Adapts the M3 decoder to the generic M2 receive-only content boundary. */
internal class ReceiveOnlyProtobufDecoderAdapter(
    private val decoder: ProtobufMessageContentDecoder
) : MessageContentDecoder<DecodedProtobufContent> {
    override fun decode(serializedContent: ByteArray): DecodedMessageContent<DecodedProtobufContent> {
        val decoded = decoder.decode(serializedContent)
        return if (decoded.classification == DecodedProtobufContent.Classification.EXTERNAL_INSTRUCTIONS) {
            val instructions = decoded.content as? ProtoContent.ExternalMessageInstructions
                ?: return DecodedMessageContent.Application(decoded)
            DecodedMessageContent.ExternalInstructions(instructions.otrKey)
        } else {
            DecodedMessageContent.Application(decoded)
        }
    }
}

internal sealed interface NotificationInboxStoreAccessResult {
    data class Opened(val store: NotificationInboxStore) : NotificationInboxStoreAccessResult
    data object RetryableFailure : NotificationInboxStoreAccessResult
    data object TerminalFailure : NotificationInboxStoreAccessResult
}

/** Store access is lazy so first open happens only after the M4 engine owns the M5 lease. */
internal interface NotificationInboxStoreProvider {
    suspend fun get(): NotificationInboxStoreAccessResult

    /** Must be idempotent, non-throwing, and called before the process lease is released. */
    fun close()
}

/** Maps the versioned M6 contract into M4 without exposing SQLDelight generated types. */
internal class NotificationInboxSyncAdapter(
    private val provider: NotificationInboxStoreProvider,
    private val deliverySource: RawEnvelopeDeliverySource,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : NotificationSyncInbox {
    override suspend fun readCursor(scope: NotificationSyncScope): InboxReadResult<NotificationSyncCursor?> =
        withStoreRead { store ->
            when (val result = store.readCursor(scope.toInboxScope())) {
                is StoreReadResult.Success -> InboxReadResult.Success(result.value?.let { NotificationSyncCursor(it.value) })
                is StoreReadResult.StorageFailure -> result.reason.toSyncReadFailure()
            }
        }

    override suspend fun stageRawEventAndAdvanceCursor(
        scope: NotificationSyncScope,
        event: RawNotificationEvent
    ): StageResult = when (val access = provider.get()) {
        is NotificationInboxStoreAccessResult.Opened -> when (
            val result = access.store.stageRawEvent(
                RawEventWrite(
                    scope = scope.toInboxScope(),
                    serverEventId = event.key.serverEventId,
                    rawEnvelope = event.rawEnvelope,
                    rawEnvelopeFormatVersion = NOTIFICATION_RAW_ENVELOPE_FORMAT_VERSION,
                    serverTimestampEpochMillis = null,
                    isTransient = event.isTransient,
                    associatedCursor = event.cursor?.value,
                    deliverySource = deliverySource,
                    receivedAtEpochMillis = nowEpochMillis()
                )
            )
        ) {
            is RawEventStageResult.Inserted -> StageResult.Durable(DurableStageStatus.INSERTED)
            is RawEventStageResult.ExactDuplicate -> StageResult.Durable(DurableStageStatus.ALREADY_STAGED)
            RawEventStageResult.IntegrityConflict -> StageResult.Conflict
            is RawEventStageResult.StorageFailure -> result.reason.toSyncStageFailure()
        }
        NotificationInboxStoreAccessResult.RetryableFailure -> StageResult.RetryableFailure
        NotificationInboxStoreAccessResult.TerminalFailure -> StageResult.TerminalFailure
    }

    override suspend fun readPendingReceiveBatch(
        scope: NotificationSyncScope,
        limit: Int
    ): InboxReadResult<PendingReceiveBatch> = withStoreRead { store ->
        when (val result = store.readPendingReceive(scope.toInboxScope(), limit)) {
            is StoreReadResult.Success -> InboxReadResult.Success(
                PendingReceiveBatch(
                    events = result.value.events.map { row ->
                        StagedNotificationEvent(
                            key = NotificationEventKey(row.serverEventId),
                            rawEnvelope = row.rawEnvelope,
                            isTransient = row.isTransient,
                            cursor = row.associatedCursor?.let(::NotificationSyncCursor)
                        )
                    },
                    hasMore = result.value.hasMore
                )
            )
            is StoreReadResult.StorageFailure -> result.reason.toSyncReadFailure()
        }
    }

    override suspend fun markReceiveProcessingCompleted(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey
    ): InboxWriteResult = withStoreMutation { store ->
        store.markReceiveCompleted(scope.toInboxScope(), eventKey.serverEventId)
    }

    override suspend fun markGlobalForegroundRecoveryRequired(
        scope: NotificationSyncScope,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult = withStoreMutation { store ->
        store.requireCursorGlobalRecovery(scope.toInboxScope(), reason.name, nowEpochMillis())
    }

    override suspend fun markEventDeferredToForeground(
        scope: NotificationSyncScope,
        eventKey: NotificationEventKey,
        reason: ForegroundRecoveryReason
    ): InboxWriteResult = withStoreMutation { store ->
        store.deferToForeground(scope.toInboxScope(), eventKey.serverEventId, reason.name)
    }

    private suspend fun <T> withStoreRead(
        block: suspend (NotificationInboxStore) -> InboxReadResult<T>
    ): InboxReadResult<T> = when (val access = provider.get()) {
        is NotificationInboxStoreAccessResult.Opened -> block(access.store)
        NotificationInboxStoreAccessResult.RetryableFailure -> InboxReadResult.RetryableFailure
        NotificationInboxStoreAccessResult.TerminalFailure -> InboxReadResult.TerminalFailure
    }

    private suspend fun withStoreMutation(
        block: suspend (NotificationInboxStore) -> StoreMutationResult
    ): InboxWriteResult = when (val access = provider.get()) {
        is NotificationInboxStoreAccessResult.Opened -> block(access.store).toSyncWriteResult()
        NotificationInboxStoreAccessResult.RetryableFailure -> InboxWriteResult.RetryableFailure
        NotificationInboxStoreAccessResult.TerminalFailure -> InboxWriteResult.TerminalFailure
    }
}

internal sealed interface ReceiveOnlyStagedEventResult {
    data class Children(val children: List<ReceiveChildWrite>) : ReceiveOnlyStagedEventResult
    data object NoChildren : ReceiveOnlyStagedEventResult
    data class ForegroundRequired(val reason: ForegroundRecoveryReason) : ReceiveOnlyStagedEventResult
    data object RetryableFailure : ReceiveOnlyStagedEventResult
    data object TerminalFailure : ReceiveOnlyStagedEventResult
}

/**
 * Production implementations may only apply already-received Proteus/MLS bytes and pure M3
 * extraction. Sending, receipts, delayed commits, CRL fetching, and active recovery are excluded.
 */
internal fun interface ReceiveOnlyStagedEventHandler {
    suspend fun receive(event: StagedNotificationEvent): ReceiveOnlyStagedEventResult
}

/** Commits the complete M2/M3 receive output batch through M6 before M4 marks the parent done. */
internal class NotificationInboxEventProcessor(
    private val scope: InboxScope,
    private val provider: NotificationInboxStoreProvider,
    private val handler: ReceiveOnlyStagedEventHandler
) : StagedNotificationEventProcessor {
    override suspend fun process(event: StagedNotificationEvent): StagedEventProcessingResult =
        when (val result = handler.receive(event)) {
            is ReceiveOnlyStagedEventResult.Children -> stageChildren(event, result.children)
            ReceiveOnlyStagedEventResult.NoChildren -> StagedEventProcessingResult.DurablyMaterialized
            is ReceiveOnlyStagedEventResult.ForegroundRequired ->
                StagedEventProcessingResult.ForegroundRequired(result.reason)
            ReceiveOnlyStagedEventResult.RetryableFailure -> StagedEventProcessingResult.RetryableFailure
            ReceiveOnlyStagedEventResult.TerminalFailure -> StagedEventProcessingResult.TerminalFailure
        }

    private suspend fun stageChildren(
        event: StagedNotificationEvent,
        children: List<ReceiveChildWrite>
    ): StagedEventProcessingResult = when (val access = provider.get()) {
        is NotificationInboxStoreAccessResult.Opened -> when (
            val result = access.store.stageReceiveChildren(
                ReceiveChildrenWrite(scope, event.key.serverEventId, children)
            )
        ) {
            is ReceiveChildrenStageResult.Stored -> StagedEventProcessingResult.DurablyMaterialized
            ReceiveChildrenStageResult.ParentMissing,
            ReceiveChildrenStageResult.IntegrityConflict -> StagedEventProcessingResult.TerminalFailure
            is ReceiveChildrenStageResult.StorageFailure -> if (result.reason.isRetryable()) {
                StagedEventProcessingResult.RetryableFailure
            } else {
                StagedEventProcessingResult.TerminalFailure
            }
        }
        NotificationInboxStoreAccessResult.RetryableFailure -> StagedEventProcessingResult.RetryableFailure
        NotificationInboxStoreAccessResult.TerminalFailure -> StagedEventProcessingResult.TerminalFailure
    }
}

internal fun syntheticReceiveChild(
    scope: InboxScope,
    parentServerEventId: String,
    idempotencyKey: String,
    decryptedProto: ByteArray
): ReceiveChildWrite = ReceiveChildWrite(
    scope = scope,
    parentServerEventId = parentServerEventId,
    itemIndex = 0,
    idempotencyKey = idempotencyKey,
    conversationId = null,
    senderId = null,
    senderClientId = null,
    protocol = com.wire.kalium.notificationinbox.ReceiveProtocol.NONE,
    messageTimestampEpochMillis = null,
    decryptedProto = decryptedProto,
    cryptoStateApplied = true,
    receiveClassification = com.wire.kalium.notificationinbox.ReceiveClassification.APPLICATION_MESSAGE,
    failureClassification = null,
    decryptionState = DecryptionState.DECRYPTED,
    notificationState = NotificationState.SUPPRESSED,
    importState = ForegroundImportState.PENDING,
    retryCount = 0
)

private fun NotificationExtensionRequest.toDomainRequest(): BoundedNotificationSyncRequest? {
    if (!isValid()) return null
    return BoundedNotificationSyncRequest(
        scope = NotificationSyncScope(accountId, clientId),
        markerId = markerId,
        absoluteDeadline = Instant.fromEpochMilliseconds(absoluteDeadlineEpochMillis),
        budget = NotificationSyncBudget(
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
    )
}

private fun NotificationExtensionRequest.isValid(): Boolean {
    val identifiersAreValid = accountId.isNotBlank() && clientId.isNotBlank() && markerId.isNotBlank()
    val correlationIsValid = opaqueCorrelationId == null || opaqueCorrelationId.validatedOpaqueCorrelationId() != null
    val countsAreValid = listOf(
        maxTransportFrames,
        maxEventsToStage,
        maxDrainBatches,
        maxEventsPerDrainBatch
    ).all { it > 0 }
    val byteBudgetsAreValid = maxRawEnvelopeBytes > 0 && maxRawEnvelopeBytesPerRun > 0 &&
            maxDrainRawEnvelopeBytesPerRun > 0
    return identifiersAreValid && correlationIsValid && countsAreValid && byteBudgetsAreValid &&
            deadlineSafetyMarginMillis >= 0 &&
            maxRunDurationMillis > 0
}

private fun BoundedNotificationSyncResult.toExtensionResult(): NotificationExtensionResult {
    val status = when (this) {
        is BoundedNotificationSyncResult.Complete -> NotificationExtensionStatus.COMPLETE
        is BoundedNotificationSyncResult.Partial -> NotificationExtensionStatus.PARTIAL
        is BoundedNotificationSyncResult.LockUnavailable -> NotificationExtensionStatus.LOCK_UNAVAILABLE
        is BoundedNotificationSyncResult.DeadlineReached -> NotificationExtensionStatus.DEADLINE_REACHED
        is BoundedNotificationSyncResult.ForegroundRecoveryRequired ->
            NotificationExtensionStatus.FOREGROUND_RECOVERY_REQUIRED
        is BoundedNotificationSyncResult.TerminalFailure -> NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE
    }
    val reason = when (this) {
        is BoundedNotificationSyncResult.Complete,
        is BoundedNotificationSyncResult.LockUnavailable -> NotificationExtensionReason.NONE
        is BoundedNotificationSyncResult.DeadlineReached -> NotificationExtensionReason.DEADLINE
        is BoundedNotificationSyncResult.Partial -> reason.toExtensionReason()
        is BoundedNotificationSyncResult.ForegroundRecoveryRequired -> reason.toExtensionReason()
        is BoundedNotificationSyncResult.TerminalFailure -> reason.toExtensionReason()
    }
    return NotificationExtensionResult(
        status = status,
        reason = reason,
        summary = summary.toExtensionSummary(),
        // M6 has no policy snapshot. Details must remain suppressed even after a complete sync.
        shouldUsePrivacyPreservingFallback = true
    )
}

private fun NotificationSyncSummary.toExtensionSummary(): NotificationExtensionSummary = NotificationExtensionSummary(
    transportFramesReceived = transportFramesReceived,
    eventsInserted = eventsInserted,
    eventsAlreadyStaged = eventsAlreadyStaged,
    transportAcksAcceptedByLocalWriter = transportAcksAcceptedByLocalWriter,
    eventsReceiveMaterialized = eventsReceiveMaterialized,
    drainBatchesRead = drainBatchesRead,
    transportRawEnvelopeBytesReceived = transportRawEnvelopeBytesReceived,
    drainRawEnvelopeBytesRead = drainRawEnvelopeBytesRead
)

private fun PartialSyncReason.toExtensionReason(): NotificationExtensionReason = when (this) {
    PartialSyncReason.LEASE_ACQUISITION_FAILED -> NotificationExtensionReason.LEASE_ACQUISITION_FAILED
    PartialSyncReason.TRANSPORT_OPEN_FAILED -> NotificationExtensionReason.TRANSPORT_OPEN_FAILED
    PartialSyncReason.TRANSPORT_RECEIVE_FAILED -> NotificationExtensionReason.TRANSPORT_RECEIVE_FAILED
    PartialSyncReason.TRANSPORT_CLOSED -> NotificationExtensionReason.TRANSPORT_CLOSED
    PartialSyncReason.TRANSPORT_ACK_REJECTED -> NotificationExtensionReason.TRANSPORT_ACK_REJECTED
    PartialSyncReason.STORAGE_FAILED -> NotificationExtensionReason.STORAGE_FAILED
    PartialSyncReason.PROCESSING_FAILED -> NotificationExtensionReason.PROCESSING_FAILED
    PartialSyncReason.EVENT_BUDGET_EXHAUSTED -> NotificationExtensionReason.EVENT_BUDGET_EXHAUSTED
    PartialSyncReason.TRANSPORT_FRAME_BUDGET_EXHAUSTED ->
        NotificationExtensionReason.TRANSPORT_FRAME_BUDGET_EXHAUSTED
    PartialSyncReason.BATCH_BUDGET_EXHAUSTED -> NotificationExtensionReason.BATCH_BUDGET_EXHAUSTED
    PartialSyncReason.EVENT_BYTE_BUDGET_EXHAUSTED -> NotificationExtensionReason.EVENT_BYTE_BUDGET_EXHAUSTED
    PartialSyncReason.DRAIN_BYTE_BUDGET_EXHAUSTED -> NotificationExtensionReason.DRAIN_BYTE_BUDGET_EXHAUSTED
    PartialSyncReason.UNEXPECTED_TRANSPORT_PAYLOAD -> NotificationExtensionReason.UNEXPECTED_TRANSPORT_PAYLOAD
}

private fun ForegroundRecoveryReason.toExtensionReason(): NotificationExtensionReason = when (this) {
    ForegroundRecoveryReason.MISSED_NOTIFICATION -> NotificationExtensionReason.MISSED_NOTIFICATION
    ForegroundRecoveryReason.LEGACY_CATCH_UP_NOT_PROVEN ->
        NotificationExtensionReason.LEGACY_CATCH_UP_NOT_PROVEN
    ForegroundRecoveryReason.EVENT_PROCESSING_DEFERRED -> NotificationExtensionReason.EVENT_PROCESSING_DEFERRED
}

private fun TerminalSyncFailureReason.toExtensionReason(): NotificationExtensionReason = when (this) {
    TerminalSyncFailureReason.INVALID_REQUEST -> NotificationExtensionReason.INVALID_REQUEST
    TerminalSyncFailureReason.LEASE_FAILURE -> NotificationExtensionReason.LEASE_FAILURE
    TerminalSyncFailureReason.TRANSPORT_CONFIGURATION -> NotificationExtensionReason.TRANSPORT_CONFIGURATION
    TerminalSyncFailureReason.STORAGE_CONFIGURATION -> NotificationExtensionReason.STORAGE_CONFIGURATION
    TerminalSyncFailureReason.RAW_EVENT_INTEGRITY_CONFLICT -> NotificationExtensionReason.RAW_EVENT_INTEGRITY_CONFLICT
    TerminalSyncFailureReason.PROCESSOR_CONFIGURATION -> NotificationExtensionReason.PROCESSOR_CONFIGURATION
}

private fun invalidRequestResult(): NotificationExtensionResult = NotificationExtensionResult(
    status = NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE,
    reason = NotificationExtensionReason.INVALID_REQUEST,
    summary = NotificationExtensionSummary.Empty,
    shouldUsePrivacyPreservingFallback = true
)

private fun NotificationSyncScope.toInboxScope(): InboxScope = InboxScope(accountId, clientId)

private fun NotificationInboxFailure.isRetryable(): Boolean = this == NotificationInboxFailure.STORAGE_UNAVAILABLE

private fun NotificationInboxFailure.toSyncReadFailure(): InboxReadResult<Nothing> =
    if (isRetryable()) InboxReadResult.RetryableFailure else InboxReadResult.TerminalFailure

private fun NotificationInboxFailure.toSyncStageFailure(): StageResult =
    if (isRetryable()) StageResult.RetryableFailure else StageResult.TerminalFailure

private fun StoreMutationResult.toSyncWriteResult(): InboxWriteResult = when (this) {
    StoreMutationResult.Success -> InboxWriteResult.Success
    StoreMutationResult.Missing,
    StoreMutationResult.IntegrityConflict -> InboxWriteResult.TerminalFailure
    is StoreMutationResult.StorageFailure -> if (reason.isRetryable()) {
        InboxWriteResult.RetryableFailure
    } else {
        InboxWriteResult.TerminalFailure
    }
}
