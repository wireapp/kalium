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
import kotlin.concurrent.atomics.AtomicInt

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
    public val maxTransportFrames: Int = DEFAULT_MAX_TRANSPORT_FRAMES,
    public val maxEventsToStage: Int = DEFAULT_MAX_EVENTS_TO_STAGE,
    public val maxDrainBatches: Int = DEFAULT_MAX_DRAIN_BATCHES,
    public val maxEventsPerDrainBatch: Int = DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH,
    public val deadlineSafetyMarginMillis: Long = DEFAULT_DEADLINE_SAFETY_MARGIN_MILLIS
) {
    override fun toString(): String = "NotificationExtensionRequest(redacted)"

    public companion object {
        public const val DEFAULT_MAX_TRANSPORT_FRAMES: Int = 200
        public const val DEFAULT_MAX_EVENTS_TO_STAGE: Int = 100
        public const val DEFAULT_MAX_DRAIN_BATCHES: Int = 4
        public const val DEFAULT_MAX_EVENTS_PER_DRAIN_BATCH: Int = 25
        public const val DEFAULT_DEADLINE_SAFETY_MARGIN_MILLIS: Long = 2_000L
    }
}

public enum class NotificationExtensionStatus {
    COMPLETE,
    PARTIAL,
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
    PRODUCTION_GATES_OPEN
}

public data class NotificationExtensionSummary(
    public val transportFramesReceived: Int,
    public val eventsInserted: Int,
    public val eventsAlreadyStaged: Int,
    public val transportAcksAcceptedByLocalWriter: Int,
    public val eventsReceiveMaterialized: Int,
    public val drainBatchesRead: Int
) {
    public companion object {
        public val Empty: NotificationExtensionSummary = NotificationExtensionSummary(0, 0, 0, 0, 0, 0)
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
    ): NotificationExtensionRunHandle {
        val cancellationKind = AtomicInt(NOT_CANCELLED)
        val completionGate = CompletionGate(completion)
        val job = scope.launch {
            val result = try {
                runtime.execute(request)
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
    public val sharedAppGroupRoot: String
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
    PHYSICAL_DEVICE_VALIDATION(1L shl PHYSICAL_DEVICE_VALIDATION_BIT)
}

/** A concrete fail-closed construction result rather than an `Either`. */
public class NotificationExtensionConstruction internal constructor(
    public val instance: NotificationExtension?,
    public val blockedGateMask: Long
) {
    public val isAvailable: Boolean get() = instance != null && blockedGateMask == NO_BLOCKED_GATES

    public fun isBlockedBy(gate: NotificationExtensionProductionGate): Boolean =
        blockedGateMask and gate.bitMask != NO_BLOCKED_GATES
}

/**
 * Production remains deliberately non-constructible in this spike.
 *
 * Calling this method performs no lock, storage, authentication, transport, or CoreCrypto access.
 */
public object NotificationExtensionFactory {
    @Suppress("UnusedParameter")
    public fun createProduction(
        configuration: NotificationExtensionHostConfiguration
    ): NotificationExtensionConstruction = NotificationExtensionConstruction(
        instance = null,
        blockedGateMask = NotificationExtensionProductionGate.entries.fold(NO_BLOCKED_GATES) { mask, gate ->
            mask or gate.bitMask
        }
    )
}

internal fun interface NotificationExtensionRuntime {
    suspend fun execute(request: NotificationExtensionRequest): NotificationExtensionResult
}

private class CompletionGate(
    private val completion: NotificationExtensionCompletion
) {
    private val state = AtomicInt(COMPLETION_PENDING)

    fun complete(result: NotificationExtensionResult) {
        if (!state.compareAndSet(COMPLETION_PENDING, COMPLETION_DELIVERED)) return
        runCatching { completion.complete(result) }
    }
}

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
