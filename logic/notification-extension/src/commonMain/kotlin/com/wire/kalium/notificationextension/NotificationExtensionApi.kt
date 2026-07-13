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

@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.wire.kalium.notificationextension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Swift-friendly input for one finite Notification Service Extension invocation.
 *
 * The absolute deadline must reserve enough time for Swift to invoke the iOS content handler.
 * Identifiers are never logged or used directly as path components by this module.
 */
@Suppress("LongParameterList")
public data class NotificationExtensionRequest(
    public val accountId: String,
    public val clientId: String,
    public val markerId: String,
    public val absoluteDeadlineEpochMillis: Long,
    public val rolloutControl: NotificationExtensionRolloutControl = NotificationExtensionRolloutControl.Unavailable,
    public val opaqueCorrelationId: String? = null,
    public val maxTransportFrames: Int = DEFAULT_MAX_TRANSPORT_FRAMES,
    public val maxEventsToStage: Int = DEFAULT_MAX_EVENTS_TO_STAGE,
    public val maxDrainBatches: Int = DEFAULT_MAX_DRAIN_BATCHES,
    public val maxEventsPerDrainBatch: Int = DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH,
    public val maxRawEnvelopeBytes: Int = DEFAULT_MAX_RAW_ENVELOPE_BYTES,
    public val maxRawEnvelopeBytesPerRun: Long = DEFAULT_MAX_RAW_ENVELOPE_BYTES_PER_RUN,
    public val maxDrainRawEnvelopeBytesPerRun: Long = DEFAULT_MAX_DRAIN_RAW_ENVELOPE_BYTES_PER_RUN,
    public val deadlineSafetyMarginMillis: Long = DEFAULT_DEADLINE_SAFETY_MARGIN_MILLIS,
    public val maxRunDurationMillis: Long = DEFAULT_MAX_RUN_DURATION_MILLIS
) {
    override fun toString(): String = "NotificationExtensionRequest(redacted)"

    public companion object {
        public const val DEFAULT_MAX_TRANSPORT_FRAMES: Int = 200
        public const val DEFAULT_MAX_EVENTS_TO_STAGE: Int = 100
        public const val DEFAULT_MAX_DRAIN_BATCHES: Int = 4
        public const val DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH: Int = 25
        public const val DEFAULT_MAX_RAW_ENVELOPE_BYTES: Int = 256 * 1_024
        public const val DEFAULT_MAX_RAW_ENVELOPE_BYTES_PER_RUN: Long = 4L * 1_024L * 1_024L
        public const val DEFAULT_MAX_DRAIN_RAW_ENVELOPE_BYTES_PER_RUN: Long = 4L * 1_024L * 1_024L
        public const val DEFAULT_DEADLINE_SAFETY_MARGIN_MILLIS: Long = 2_000L
        public const val DEFAULT_MAX_RUN_DURATION_MILLIS: Long = 20_000L
    }
}

public enum class NotificationExtensionStatus {
    COMPLETE,
    PARTIAL,
    ROLLOUT_DISABLED,
    LOCK_UNAVAILABLE,
    DEADLINE_REACHED,
    FOREGROUND_RECOVERY_REQUIRED,
    CONFIGURATION_UNAVAILABLE,
    CANCELLED
}

/** Stable, payload-free reason codes suitable for Swift metrics and fallback selection. */
public enum class NotificationExtensionReason {
    NONE,
    INVALID_REQUEST,
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
    EVENT_BYTE_BUDGET_EXHAUSTED,
    DRAIN_BYTE_BUDGET_EXHAUSTED,
    UNEXPECTED_TRANSPORT_PAYLOAD,
    MISSED_NOTIFICATION,
    LEGACY_CATCH_UP_NOT_PROVEN,
    EVENT_PROCESSING_DEFERRED,
    LEASE_FAILURE,
    TRANSPORT_CONFIGURATION,
    STORAGE_CONFIGURATION,
    RAW_EVENT_INTEGRITY_CONFLICT,
    PROCESSOR_CONFIGURATION,
    DEADLINE,
    HOST_CANCELLED,
    RUNTIME_FAILURE,
    ROLLOUT_CONTROL_VERSION_UNSUPPORTED,
    ROLLOUT_CONTROL_UNAVAILABLE,
    ROLLOUT_CONTROL_INVALID,
    ROLLOUT_CONTROL_STALE,
    ROLLOUT_KILL_SWITCH,
    ROLLOUT_FEATURE_DISABLED,
    ROLLOUT_COHORT_EXCLUDED,
    PRODUCTION_GATES_OPEN
}

public data class NotificationExtensionSummary(
    public val transportFramesReceived: Int,
    public val eventsInserted: Int,
    public val eventsAlreadyStaged: Int,
    public val transportAcksAcceptedByLocalWriter: Int,
    public val eventsReceiveMaterialized: Int,
    public val drainBatchesRead: Int,
    public val transportRawEnvelopeBytesReceived: Long,
    public val drainRawEnvelopeBytesRead: Long
) {
    public companion object {
        public val Empty: NotificationExtensionSummary = NotificationExtensionSummary(0, 0, 0, 0, 0, 0, 0L, 0L)
    }
}

/**
 * Concrete result returned to Swift. It contains no `Either`, SQLDelight, CoreCrypto, protobuf,
 * transport, or AVS domain type.
 */
public data class NotificationExtensionResult(
    public val status: NotificationExtensionStatus,
    public val reason: NotificationExtensionReason,
    public val summary: NotificationExtensionSummary,
    public val shouldUsePrivacyPreservingFallback: Boolean
)

/** Completion is invoked at most once for one [NotificationExtensionRunHandle]. */
public fun interface NotificationExtensionCompletion {
    public fun complete(result: NotificationExtensionResult)
}

/**
 * Handle owned by the host NSE.
 *
 * Cancellation is cooperative. Swift must retain its own at-most-once wrapper around the actual
 * `UNNotificationContent` handler because iOS can terminate the process while native work is in
 * progress. Both methods are idempotent and never invoke the Kotlin completion directly; the
 * worker completes only after its cancellation cleanup has run.
 */
public class NotificationExtensionRunHandle internal constructor(
    private val job: Job,
    private val cancellationKind: AtomicInt
) {
    public fun cancel(): Unit = cancelWith(CANCELLED_BY_HOST)

    public fun cancelForExpiration(): Unit = cancelWith(CANCELLED_FOR_EXPIRATION)

    private fun cancelWith(kind: Int) {
        cancellationKind.compareAndSet(NOT_CANCELLED, kind)
        job.cancel()
    }
}

/**
 * One narrow NSE entry point. Instances are created only by a verified assembly.
 *
 * Production construction intentionally remains unavailable while [NotificationExtensionFactory]
 * reports open gates. The internal constructor prevents a consumer from bypassing those gates.
 */
public class NotificationExtension internal constructor(
    private val runtime: NotificationExtensionRuntime,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    public fun begin(
        request: NotificationExtensionRequest,
        completion: NotificationExtensionCompletion
    ): NotificationExtensionRunHandle = beginInternal(request, observer = null, completion)

    /** Begins one run with one optional, bounded completion observation. */
    public fun beginObserved(
        request: NotificationExtensionRequest,
        observer: NotificationExtensionObserver,
        completion: NotificationExtensionCompletion
    ): NotificationExtensionRunHandle = beginInternal(request, observer, completion)

    private fun beginInternal(
        request: NotificationExtensionRequest,
        observer: NotificationExtensionObserver?,
        completion: NotificationExtensionCompletion
    ): NotificationExtensionRunHandle {
        val cancellationKind = AtomicInt(NOT_CANCELLED)
        val rolloutEvaluation = request.rolloutControl.evaluate(Clock.System.now().toEpochMilliseconds())
        val completionGate = CompletionGate(
            completion = completion,
            observer = observer,
            request = request,
            startedAt = TimeSource.Monotonic.markNow()
        )
        val job = scope.launch {
            val result = try {
                when (rolloutEvaluation) {
                    NotificationExtensionRolloutEvaluation.Enabled -> runtime.execute(request)
                    is NotificationExtensionRolloutEvaluation.Disabled -> rolloutDisabledResult(rolloutEvaluation.reason)
                    is NotificationExtensionRolloutEvaluation.Unavailable ->
                        rolloutUnavailableResult(rolloutEvaluation.reason)
                }
            } catch (_: CancellationException) {
                cancellationResult(cancellationKind.load())
            } catch (_: Throwable) {
                NotificationExtensionResult(
                    status = NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE,
                    reason = NotificationExtensionReason.RUNTIME_FAILURE,
                    summary = NotificationExtensionSummary.Empty,
                    shouldUsePrivacyPreservingFallback = true
                )
            }
            completionGate.complete(result)
        }
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                completionGate.complete(cancellationResult(cancellationKind.load()))
            }
        }
        return NotificationExtensionRunHandle(job, cancellationKind)
    }

    internal fun close() {
        scope.cancel()
    }
}

/** Host-supplied App Group root. The production factory never derives it from process home. */
public data class NotificationExtensionHostConfiguration(
    public val sharedAppGroupRoot: String,
    public val hostIntegrationReadiness: NotificationExtensionHostIntegrationReadiness =
        NotificationExtensionHostIntegrationReadiness.None
) {
    override fun toString(): String = "NotificationExtensionHostConfiguration(redacted)"
}

/** Stable construction gates; none contains credentials, paths, identifiers, or payload details. */
public enum class NotificationExtensionProductionGate(public val bitMask: Long) {
    VALIDATED_APP_GROUP_ROOT(1L shl VALIDATED_APP_GROUP_ROOT_BIT),
    ENCRYPTED_HANDOFF_STORAGE(1L shl ENCRYPTED_HANDOFF_STORAGE_BIT),
    SHARED_KEYCHAIN_AUTHENTICATION(1L shl SHARED_KEYCHAIN_AUTHENTICATION_BIT),
    RAW_EVENT_TRANSPORT_CAPTURE(1L shl RAW_EVENT_TRANSPORT_CAPTURE_BIT),
    LOCAL_WRITER_ACK_GUARANTEE(1L shl LOCAL_WRITER_ACK_GUARANTEE_BIT),
    RECEIVE_ONLY_CRYPTO_ASSEMBLY(1L shl RECEIVE_ONLY_CRYPTO_ASSEMBLY_BIT),
    CORE_CRYPTO_HANDOFF_CRASH_ORDERING(1L shl CORE_CRYPTO_HANDOFF_CRASH_ORDERING_BIT),
    NOTIFICATION_POLICY_SNAPSHOT(1L shl NOTIFICATION_POLICY_SNAPSHOT_BIT),
    NOTIFICATION_AVS_SWIFT_BRIDGE(1L shl NOTIFICATION_AVS_SWIFT_BRIDGE_BIT),
    SIGNED_APP_AND_NSE_ENTITLEMENTS(1L shl SIGNED_APP_AND_NSE_ENTITLEMENTS_BIT),
    PHYSICAL_DEVICE_VALIDATION(1L shl PHYSICAL_DEVICE_VALIDATION_BIT),
    BOUNDED_STORAGE_ENFORCEMENT(1L shl BOUNDED_STORAGE_ENFORCEMENT_BIT),
    FOREGROUND_CURSOR_CUTOVER(1L shl FOREGROUND_CURSOR_CUTOVER_BIT),
    ACCOUNT_REMOVAL_TOMBSTONE(1L shl ACCOUNT_REMOVAL_TOMBSTONE_BIT),
    GLOBAL_RECOVERY_FOREGROUND_ACK(1L shl GLOBAL_RECOVERY_FOREGROUND_ACK_BIT),
    PHYSICAL_DEVICE_BUDGET_APPROVAL(1L shl PHYSICAL_DEVICE_BUDGET_APPROVAL_BIT),
    NATIVE_ROLLOUT_CONTROL_OWNERSHIP(1L shl NATIVE_ROLLOUT_CONTROL_OWNERSHIP_BIT),
    APPROVED_GENERIC_FALLBACK_AND_REPLACEMENT(1L shl APPROVED_GENERIC_FALLBACK_AND_REPLACEMENT_BIT),
    PRIVACY_DIAGNOSTICS_RETENTION_AND_EXPORT(1L shl PRIVACY_DIAGNOSTICS_RETENTION_AND_EXPORT_BIT),
    CURSOR_CUTOVER_AND_DOWNGRADE_RELEASE(1L shl CURSOR_CUTOVER_AND_DOWNGRADE_RELEASE_BIT),
    ROLLOUT_STOP_CONDITIONS_APPROVAL(1L shl ROLLOUT_STOP_CONDITIONS_APPROVAL_BIT)
}

/** A concrete fail-closed construction result rather than an `Either`. */
public class NotificationExtensionConstruction internal constructor(
    public val instance: NotificationExtension?,
    public val blockedGateMask: Long,
    public val missingHostResponsibilityMask: Long
) {
    public val isAvailable: Boolean
        get() = instance != null && blockedGateMask == NO_BLOCKED_GATES &&
                missingHostResponsibilityMask == NO_MISSING_HOST_RESPONSIBILITIES

    public fun isBlockedBy(gate: NotificationExtensionProductionGate): Boolean =
        blockedGateMask and gate.bitMask != NO_BLOCKED_GATES

    public fun isMissing(responsibility: NotificationExtensionHostResponsibility): Boolean =
        missingHostResponsibilityMask and responsibility.bitMask != NO_MISSING_HOST_RESPONSIBILITIES
}

/**
 * Production remains deliberately non-constructible in this spike.
 *
 * Calling this method performs no lock, storage, authentication, transport, or CoreCrypto access.
 */
public object NotificationExtensionFactory {
    public fun createProduction(
        configuration: NotificationExtensionHostConfiguration
    ): NotificationExtensionConstruction = NotificationExtensionConstruction(
        instance = null,
        blockedGateMask = NotificationExtensionProductionGate.entries.fold(NO_BLOCKED_GATES) { mask, gate ->
            mask or gate.bitMask
        },
        missingHostResponsibilityMask = allHostResponsibilityMask and
                configuration.hostIntegrationReadiness.fulfilledResponsibilityMask.inv()
    )
}

internal fun interface NotificationExtensionRuntime {
    suspend fun execute(request: NotificationExtensionRequest): NotificationExtensionResult
}

private class CompletionGate(
    private val completion: NotificationExtensionCompletion,
    private val observer: NotificationExtensionObserver?,
    private val request: NotificationExtensionRequest,
    private val startedAt: TimeMark
) {
    private val state = AtomicInt(COMPLETION_PENDING)

    fun complete(result: NotificationExtensionResult) {
        if (!state.compareAndSet(COMPLETION_PENDING, COMPLETION_DELIVERED)) return
        runCatching { completion.complete(result) }
        // Completion is invoked before even constructing diagnostics so no observation-side
        // failure can suppress or delay entry into the NSE content handler. The sink is
        // constrained to one fixed-shape call per invocation.
        val sink = observer ?: return
        runCatching {
            sink.observe(result.toObservation(request, startedAt.elapsedNow().inWholeMilliseconds))
        }
    }
}

private fun NotificationExtensionResult.toObservation(
    request: NotificationExtensionRequest,
    elapsedMillis: Long
): NotificationExtensionObservation {
    val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
    val deadlineMarginMillis = if (request.absoluteDeadlineEpochMillis <= nowEpochMillis) {
        0L
    } else {
        request.absoluteDeadlineEpochMillis - nowEpochMillis
    }
    return NotificationExtensionObservation(
        contractVersion = NOTIFICATION_EXTENSION_OBSERVATION_CONTRACT_VERSION,
        opaqueCorrelationId = request.opaqueCorrelationId.validatedOpaqueCorrelationId(),
        status = status,
        reason = reason,
        duration = durationBucket(elapsedMillis.coerceAtLeast(0L)),
        deadlineMargin = deadlineMarginBucket(deadlineMarginMillis),
        transportFramesReceived = countBucket(summary.transportFramesReceived),
        eventsInserted = countBucket(summary.eventsInserted),
        eventsAlreadyStaged = countBucket(summary.eventsAlreadyStaged),
        transportAcksAcceptedByLocalWriter = countBucket(summary.transportAcksAcceptedByLocalWriter),
        eventsReceiveMaterialized = countBucket(summary.eventsReceiveMaterialized),
        drainBatchesRead = countBucket(summary.drainBatchesRead)
    )
}

private fun rolloutDisabledResult(reason: NotificationExtensionReason): NotificationExtensionResult =
    NotificationExtensionResult(
        status = NotificationExtensionStatus.ROLLOUT_DISABLED,
        reason = reason,
        summary = NotificationExtensionSummary.Empty,
        shouldUsePrivacyPreservingFallback = true
    )

private fun rolloutUnavailableResult(reason: NotificationExtensionReason): NotificationExtensionResult =
    NotificationExtensionResult(
        status = NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE,
        reason = reason,
        summary = NotificationExtensionSummary.Empty,
        shouldUsePrivacyPreservingFallback = true
    )

private fun cancellationResult(kind: Int): NotificationExtensionResult = NotificationExtensionResult(
    status = if (kind == CANCELLED_FOR_EXPIRATION) {
        NotificationExtensionStatus.DEADLINE_REACHED
    } else {
        NotificationExtensionStatus.CANCELLED
    },
    reason = if (kind == CANCELLED_FOR_EXPIRATION) {
        NotificationExtensionReason.DEADLINE
    } else {
        NotificationExtensionReason.HOST_CANCELLED
    },
    summary = NotificationExtensionSummary.Empty,
    shouldUsePrivacyPreservingFallback = true
)

private const val NOT_CANCELLED = 0
private const val CANCELLED_BY_HOST = 1
private const val CANCELLED_FOR_EXPIRATION = 2
private const val COMPLETION_PENDING = 0
private const val COMPLETION_DELIVERED = 1
private const val NO_BLOCKED_GATES = 0L
private const val NO_MISSING_HOST_RESPONSIBILITIES = 0L
private const val VALIDATED_APP_GROUP_ROOT_BIT = 0
private const val ENCRYPTED_HANDOFF_STORAGE_BIT = 1
private const val SHARED_KEYCHAIN_AUTHENTICATION_BIT = 2
private const val RAW_EVENT_TRANSPORT_CAPTURE_BIT = 3
private const val LOCAL_WRITER_ACK_GUARANTEE_BIT = 4
private const val RECEIVE_ONLY_CRYPTO_ASSEMBLY_BIT = 5
private const val CORE_CRYPTO_HANDOFF_CRASH_ORDERING_BIT = 6
private const val NOTIFICATION_POLICY_SNAPSHOT_BIT = 7
private const val NOTIFICATION_AVS_SWIFT_BRIDGE_BIT = 8
private const val SIGNED_APP_AND_NSE_ENTITLEMENTS_BIT = 9
private const val PHYSICAL_DEVICE_VALIDATION_BIT = 10
private const val BOUNDED_STORAGE_ENFORCEMENT_BIT = 11
private const val FOREGROUND_CURSOR_CUTOVER_BIT = 12
private const val ACCOUNT_REMOVAL_TOMBSTONE_BIT = 13
private const val GLOBAL_RECOVERY_FOREGROUND_ACK_BIT = 14
private const val PHYSICAL_DEVICE_BUDGET_APPROVAL_BIT = 15
private const val NATIVE_ROLLOUT_CONTROL_OWNERSHIP_BIT = 16
private const val APPROVED_GENERIC_FALLBACK_AND_REPLACEMENT_BIT = 17
private const val PRIVACY_DIAGNOSTICS_RETENTION_AND_EXPORT_BIT = 18
private const val CURSOR_CUTOVER_AND_DOWNGRADE_RELEASE_BIT = 19
private const val ROLLOUT_STOP_CONDITIONS_APPROVAL_BIT = 20
