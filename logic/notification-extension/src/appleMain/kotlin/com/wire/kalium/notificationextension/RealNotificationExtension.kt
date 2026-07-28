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

import com.wire.kalium.notificationinbox.DecryptionState
import com.wire.kalium.notificationinbox.EncryptedAppleNotificationInboxFactory
import com.wire.kalium.notificationinbox.EncryptedNotificationInboxOpenResult
import com.wire.kalium.notificationinbox.ForegroundImportState
import com.wire.kalium.notificationinbox.InboxScope
import com.wire.kalium.notificationinbox.NotificationInboxFailure
import com.wire.kalium.notificationinbox.NotificationInboxLimits
import com.wire.kalium.notificationinbox.NotificationState
import com.wire.kalium.notificationinbox.RawEnvelopeDeliverySource
import com.wire.kalium.notificationinbox.ReceiveChildWrite
import com.wire.kalium.notificationinbox.ReceiveChildrenStageResult
import com.wire.kalium.notificationinbox.ReceiveChildrenWrite
import com.wire.kalium.notificationinbox.ReceiveClassification
import com.wire.kalium.notificationinbox.ReceiveProtocol
import com.wire.kalium.notificationinbox.fallbackChildIdempotencyKey
import com.wire.kalium.notificationinbox.protocolMessageUidChildIdempotencyKey
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.notificationextension.NotificationExtensionCoreLogic
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicBridge
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicBridgeUnsafeTeardownException
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicCallEvent
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicContentKind
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicMessage
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicMaterializationStatus
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicReceiveStatus
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportAckStatus
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportFrame
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportMode
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportOpenStatus
import com.wire.kalium.logic.notificationextension.NotificationExtensionLogicTransportReceiveStatus
import com.wire.kalium.logic.notificationextension.MainAppNotificationExtensionProcessLock
import com.wire.kalium.logic.notificationextension.MainAppNotificationExtensionProcessLockStatus
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * Shared paths and keychain identity already used by the logged-in host app.
 *
 * [kaliumRootPath] is retained for source compatibility and must exactly equal
 * [sharedAppGroupRoot]. The validated App Group root is the one canonical root used for locking,
 * Kalium, and encrypted handoff storage. The app and NSE targets must also have matching App Group
 * and Keychain Sharing entitlements.
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
    public val userDomain: String?,
    public val absoluteDeadlineEpochMillis: Long,
    public val rolloutControl: NotificationExtensionRolloutControl = NotificationExtensionRolloutControl.Unavailable,
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
        absoluteDeadlineEpochMillis: Long
    ) : this(
        userId = userId,
        userDomain = null,
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

    /** Swift-friendly constructor with no domain and an explicit fail-closed rollout snapshot. */
    public constructor(
        userId: String,
        absoluteDeadlineEpochMillis: Long,
        rolloutControl: NotificationExtensionRolloutControl
    ) : this(
        userId = userId,
        userDomain = null,
        absoluteDeadlineEpochMillis = absoluteDeadlineEpochMillis,
        rolloutControl = rolloutControl,
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

    /** Swift-friendly constructor with an explicit fail-closed rollout snapshot. */
    public constructor(
        userId: String,
        userDomain: String?,
        absoluteDeadlineEpochMillis: Long,
        rolloutControl: NotificationExtensionRolloutControl
    ) : this(
        userId = userId,
        userDomain = userDomain,
        absoluteDeadlineEpochMillis = absoluteDeadlineEpochMillis,
        rolloutControl = rolloutControl,
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

/** Final host presentation action. No decrypted candidate or message body crosses this boundary. */
public enum class RealNotificationPresentationDecision {
    PRIVACY_PRESERVING_FALLBACK
}

/**
 * Result of one real bounded attempt.
 *
 * The production policy-snapshot gate remains blocked, so the only exportable presentation
 * decision is the privacy-preserving fallback. Decrypted candidates remain inside the process and
 * can never escape on partial, deadline, cancellation, recovery, or configuration outcomes.
 */
public class RealNotificationExtensionResult internal constructor(
    public val status: NotificationExtensionStatus,
    public val reason: NotificationExtensionReason,
    public val summary: NotificationExtensionSummary,
    public val presentationDecision: RealNotificationPresentationDecision
) {
    public val shouldUsePrivacyPreservingFallback: Boolean
        get() = presentationDecision == RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK
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
 * Internal real-account implementation.
 *
 * There is deliberately no public constructor. Native code must use
 * [RealNotificationExtensionFactory], which keeps this path unavailable until every production
 * gate—including encrypted handoff storage and CoreCrypto/handoff crash ordering—is closed.
 */
public class RealNotificationExtension internal constructor(
    private val configuration: RealNotificationExtensionConfiguration,
    private val callProcessor: NotificationExtensionCallProcessor
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val beginState = AtomicInt(REAL_BEGIN_AVAILABLE)
    private val coreLogic: NotificationExtensionCoreLogic by lazy {
        NotificationExtensionCoreLogic(
            // One canonical root is locked, validated, and used for every Kalium resource.
            rootPath = configuration.sharedAppGroupRoot,
            keychainConfig = ApplePersistenceConfig(
                serviceName = configuration.keychainServiceName,
                accessGroup = configuration.keychainAccessGroup,
                accessibleAfterFirstUnlock = true
            ),
            kaliumConfigs = KaliumConfigs(),
            userAgent = configuration.userAgent
        )
    }

    public fun begin(
        request: RealNotificationExtensionRequest,
        completion: RealNotificationExtensionCompletion
    ): RealNotificationExtensionRunHandle {
        val cancellationKind = AtomicInt(REAL_NOT_CANCELLED)
        val completionGate = RealCompletionGate(completion)
        if (!claimRealNotificationExtensionBegin(beginState)) {
            completionGate.complete(unavailableResult(NotificationExtensionReason.RUNTIME_FAILURE))
            val completedJob = Job().apply { complete() }
            return RealNotificationExtensionRunHandle(completedJob, cancellationKind)
        }
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
        when (val rollout = request.rolloutControl.evaluate(Clock.System.now().toEpochMilliseconds())) {
            NotificationExtensionRolloutEvaluation.Enabled -> Unit
            is NotificationExtensionRolloutEvaluation.Disabled -> return rolloutDisabledRealResult(rollout.reason)
            is NotificationExtensionRolloutEvaluation.Unavailable -> return unavailableResult(rollout.reason)
        }
        val processLock = acquireAccountProcessLock(request)
        when (processLock.status) {
            MainAppNotificationExtensionProcessLockStatus.ACQUIRED -> Unit
            MainAppNotificationExtensionProcessLockStatus.UNAVAILABLE -> {
                processLock.release()
                return lockUnavailableResult()
            }

            MainAppNotificationExtensionProcessLockStatus.RETRYABLE_FAILURE -> {
                processLock.release()
                return unavailableResult(NotificationExtensionReason.LEASE_ACQUISITION_FAILED)
            }

            MainAppNotificationExtensionProcessLockStatus.TERMINAL_FAILURE -> {
                processLock.release()
                return unavailableResult(NotificationExtensionReason.LEASE_FAILURE)
            }
        }

        return executeRetainingAccountLockOnUnsafeTeardown(
            execution = { executeWhileProcessLockIsHeld(request) },
            retainAccountLock = { retainUnsafeAccountLockUntilProcessExit(processLock) },
            releaseAccountLock = processLock::release
        )
    }

    @Suppress("ReturnCount")
    private suspend fun executeWhileProcessLockIsHeld(
        request: RealNotificationExtensionRequest
    ): RealNotificationExtensionResult {
        val teardownState = NotificationExtensionTeardownState()
        var bridge: NotificationExtensionLogicBridge? = null
        return try {
            val userId = request.userDomain
                ?.takeIf(String::isNotBlank)
                ?.let { UserId(request.userId, it) }
                ?: coreLogic.resolveQualifiedUserId(request.userId)
                ?: return unavailableResult(NotificationExtensionReason.TRANSPORT_CONFIGURATION)
            val activeBridge = try {
                coreLogic.createBridge(userId)
            } catch (failure: NotificationExtensionLogicBridgeUnsafeTeardownException) {
                throw UnsafeRealNotificationExtensionTeardown(failure)
            } catch (failure: Throwable) {
                return unavailableResult(NotificationExtensionReason.TRANSPORT_CONFIGURATION)
            }
            bridge = activeBridge
            val clientId = activeBridge.resolveClientId()
                ?: return unavailableResult(NotificationExtensionReason.TRANSPORT_CONFIGURATION)
            val selfAvsUserId = activeBridge.resolveSelfAvsUserId()
            val markerId = Uuid.random().toString()
            val inboxScope = InboxScope(accountId = userId.toString(), clientId = clientId)
            val inboxStoreProvider = RealNotificationInboxStoreProvider(
                EncryptedAppleNotificationInboxFactory(
                    sharedAppGroupRoot = configuration.sharedAppGroupRoot,
                    scope = inboxScope,
                    key = coreLogic.getOrCreateNotificationInboxDatabaseKey(userId, clientId),
                    limits = request.toInboxLimits()
                )
            )
            val inbox = NotificationInboxSyncAdapter(
                provider = inboxStoreProvider,
                deliverySource = RawEnvelopeDeliverySource.CONSUMABLE_WEBSOCKET
            )
            val engine = BoundedNotificationSyncEngine(
                leaseCoordinator = AppleNotificationSyncLeaseCoordinator(
                    configuration.sharedAppGroupRoot,
                    closeAttemptResources = {
                        closeRealAttemptResources(
                            inboxStoreProvider::close,
                            activeBridge::close,
                            coreLogic::close
                        )
                    },
                    teardownState = teardownState
                ),
                inbox = inbox,
                transport = RealLogicNotificationTransport(activeBridge),
                eventProcessor = RealLogicEventProcessor(
                    provider = inboxStoreProvider,
                    bridge = activeBridge,
                    callProcessor = callProcessor,
                    selfUserId = selfAvsUserId,
                    selfClientId = clientId,
                    inboxScope = inboxScope
                )
            )
            val domainResult = engine.syncOnce(
                BoundedNotificationSyncRequest(
                    scope = NotificationSyncScope(accountId = userId.toString(), clientId = clientId),
                    markerId = markerId,
                    absoluteDeadline = Instant.fromEpochMilliseconds(request.absoluteDeadlineEpochMillis),
                    budget = request.toBudget()
                )
            )
            if (teardownState.isUnsafe) {
                throw UnsafeRealNotificationExtensionTeardown()
            }
            val base = domainResult.toExtensionResult()
            RealNotificationExtensionResult(
                status = base.status,
                reason = base.reason,
                summary = base.summary,
                presentationDecision = base.status.toFailClosedPresentationDecision()
            )
        } finally {
            val closeFailure = runCatching {
                closeRealAttemptResources(
                    { bridge?.close() },
                    coreLogic::close
                )
            }.exceptionOrNull()
            if (closeFailure != null || teardownState.isUnsafe) {
                throw UnsafeRealNotificationExtensionTeardown(closeFailure)
            }
        }
    }

    private suspend fun acquireAccountProcessLock(
        request: RealNotificationExtensionRequest
    ): com.wire.kalium.logic.notificationextension.MainAppNotificationExtensionProcessLockResult {
        val lock = MainAppNotificationExtensionProcessLock(configuration.sharedAppGroupRoot)
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val result = lock.tryAcquire(request.userId)
            if (result.status == MainAppNotificationExtensionProcessLockStatus.ACQUIRED ||
                result.status == MainAppNotificationExtensionProcessLockStatus.TERMINAL_FAILURE
            ) {
                return result
            }
            result.release()
            val now = Clock.System.now().toEpochMilliseconds()
            val retryDelayMillis = accountLockRetryDelayMillis(attempt++)
            if (!hasAccountLockRetryBudget(
                    absoluteDeadlineEpochMillis = request.absoluteDeadlineEpochMillis,
                    safetyMarginMillis = request.deadlineSafetyMarginMillis,
                    nowEpochMillis = now,
                    retryDelayMillis = retryDelayMillis
                )
            ) {
                return result
            }
            delay(retryDelayMillis)
        }
    }
}

/**
 * Explicit evidence owned by the native app rather than Kalium.
 *
 * Only gates in the externally-verifiable allow-list are accepted. Claiming a code-owned gate is
 * rejected and can never make the real implementation constructible.
 */
public data class RealNotificationExtensionProductionReadiness(
    public val externallyVerifiedGateMask: Long,
    public val hostIntegrationReadiness: NotificationExtensionHostIntegrationReadiness
) {
    override fun toString(): String = "RealNotificationExtensionProductionReadiness(redacted)"

    public fun verifies(gate: NotificationExtensionProductionGate): Boolean =
        externallyVerifiedGateMask and gate.bitMask != 0L

    public companion object {
        public val None: RealNotificationExtensionProductionReadiness =
            RealNotificationExtensionProductionReadiness(
                externallyVerifiedGateMask = 0L,
                hostIntegrationReadiness = NotificationExtensionHostIntegrationReadiness.None
            )

        /** Gates whose evidence necessarily comes from the signed native app/product release. */
        public val SupportedExternalGateMask: Long
            get() = EXTERNALLY_VERIFIABLE_REAL_GATE_MASK

        public fun isSupportedExternalGate(gate: NotificationExtensionProductionGate): Boolean =
            EXTERNALLY_VERIFIABLE_REAL_GATE_MASK and gate.bitMask != 0L
    }
}

/** Fail-closed production construction result for the real account wrapper. */
public class RealNotificationExtensionConstruction internal constructor(
    public val instance: RealNotificationExtension?,
    public val blockedGateMask: Long,
    public val missingHostResponsibilityMask: Long,
    public val rejectedExternalGateClaimMask: Long,
    public val isConfigurationValid: Boolean
) {
    public val isAvailable: Boolean
        get() = instance != null &&
                isConfigurationValid &&
                blockedGateMask == 0L &&
                missingHostResponsibilityMask == 0L &&
                rejectedExternalGateClaimMask == 0L

    public fun isBlockedBy(gate: NotificationExtensionProductionGate): Boolean =
        blockedGateMask and gate.bitMask != 0L

    public fun isMissing(responsibility: NotificationExtensionHostResponsibility): Boolean =
        missingHostResponsibilityMask and responsibility.bitMask != 0L

    public fun rejectedExternalClaimFor(gate: NotificationExtensionProductionGate): Boolean =
        rejectedExternalGateClaimMask and gate.bitMask != 0L
}

/**
 * Sole public construction path for the real account implementation.
 *
 * It delegates gate evaluation to the production factory. While any gate remains open it never
 * constructs account storage, CoreCrypto, transport, AVS, or the real wrapper.
 */
public object RealNotificationExtensionFactory {
    public fun createProduction(
        configuration: RealNotificationExtensionConfiguration,
        callProcessor: NotificationExtensionCallProcessor,
        readiness: RealNotificationExtensionProductionReadiness
    ): RealNotificationExtensionConstruction {
        val acceptedExternalGateMask =
            readiness.externallyVerifiedGateMask and EXTERNALLY_VERIFIABLE_REAL_GATE_MASK
        val rejectedExternalGateClaimMask =
            readiness.externallyVerifiedGateMask and EXTERNALLY_VERIFIABLE_REAL_GATE_MASK.inv()
        val blockedGateMask = NotificationExtensionProductionGate.entries.fold(0L) { mask, gate ->
            if (
                gate in REAL_IMPLEMENTATION_SATISFIED_GATES ||
                acceptedExternalGateMask and gate.bitMask != 0L
            ) {
                mask
            } else {
                mask or gate.bitMask
            }
        }
        val missingHostResponsibilityMask = allHostResponsibilityMask and
                readiness.hostIntegrationReadiness.fulfilledResponsibilityMask.inv()
        val configurationValid = configuration.isValid()
        val instance = if (
            configurationValid &&
            blockedGateMask == 0L &&
            missingHostResponsibilityMask == 0L &&
            rejectedExternalGateClaimMask == 0L
        ) {
            RealNotificationExtension(configuration, callProcessor)
        } else {
            null
        }
        return RealNotificationExtensionConstruction(
            instance = instance,
            blockedGateMask = blockedGateMask,
            missingHostResponsibilityMask = missingHostResponsibilityMask,
            rejectedExternalGateClaimMask = rejectedExternalGateClaimMask,
            isConfigurationValid = configurationValid
        )
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

private fun closeRealAttemptResources(vararg closeResources: () -> Unit) {
    val failures = closeResources.mapNotNull { close ->
        runCatching(close).exceptionOrNull()
    }
    val failure = failures.firstOrNull() ?: return
    failures.drop(1).forEach(failure::addSuppressed)
    throw UnsafeRealNotificationExtensionTeardown(failure)
}

internal class UnsafeRealNotificationExtensionTeardown(
    cause: Throwable? = null
) : IllegalStateException("Notification extension teardown was unsafe", cause)

internal suspend fun executeRetainingAccountLockOnUnsafeTeardown(
    execution: suspend () -> RealNotificationExtensionResult,
    retainAccountLock: () -> Unit,
    releaseAccountLock: () -> Unit
): RealNotificationExtensionResult {
    var retainUntilProcessExit = false
    return try {
        execution()
    } catch (_: UnsafeRealNotificationExtensionTeardown) {
        retainUntilProcessExit = true
        retainAccountLock()
        unavailableResult(NotificationExtensionReason.RUNTIME_FAILURE)
    } finally {
        if (!retainUntilProcessExit) releaseAccountLock()
    }
}

private fun retainUnsafeAccountLockUntilProcessExit(
    lock: com.wire.kalium.logic.notificationextension.MainAppNotificationExtensionProcessLockResult
) {
    while (true) {
        val current = RETAINED_UNSAFE_ACCOUNT_LOCKS.load()
        if (RETAINED_UNSAFE_ACCOUNT_LOCKS.compareAndSet(current, current + lock)) return
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
    private val provider: NotificationInboxStoreProvider,
    private val bridge: NotificationExtensionLogicBridge,
    private val callProcessor: NotificationExtensionCallProcessor,
    private val selfUserId: String,
    private val selfClientId: String,
    private val inboxScope: InboxScope
) : StagedNotificationEventProcessor {
    @Suppress("CyclomaticComplexMethod")
    override suspend fun process(event: StagedNotificationEvent): StagedEventProcessingResult {
        var childrenDurablyStaged = false
        val result = bridge.receive(event.rawEnvelope) { messages, receiveStatus ->
            val callsRequireForeground = processCalls(messages)
            val children = messages.map {
                it.toReceiveChild(
                    inboxScope,
                    requiresForeground = callsRequireForeground ||
                            receiveStatus == NotificationExtensionLogicReceiveStatus.FOREGROUND_REQUIRED
                )
            }
            when (stageChildren(event, children)) {
                NotificationExtensionLogicMaterializationStatus.DURABLE -> {
                    childrenDurablyStaged = true
                    NotificationExtensionLogicMaterializationStatus.DURABLE
                }

                NotificationExtensionLogicMaterializationStatus.RETRYABLE_FAILURE ->
                    NotificationExtensionLogicMaterializationStatus.RETRYABLE_FAILURE

                NotificationExtensionLogicMaterializationStatus.TERMINAL_FAILURE ->
                    NotificationExtensionLogicMaterializationStatus.TERMINAL_FAILURE
            }
        }
        return when (result.status) {
            NotificationExtensionLogicReceiveStatus.MATERIALIZED -> StagedEventProcessingResult.DurablyMaterialized
            NotificationExtensionLogicReceiveStatus.FOREGROUND_REQUIRED -> if (childrenDurablyStaged) {
                StagedEventProcessingResult.DurablyMaterialized
            } else {
                StagedEventProcessingResult.ForegroundRequired(ForegroundRecoveryReason.EVENT_PROCESSING_DEFERRED)
            }

            NotificationExtensionLogicReceiveStatus.RETRYABLE_FAILURE -> StagedEventProcessingResult.RetryableFailure
            NotificationExtensionLogicReceiveStatus.TERMINAL_FAILURE -> StagedEventProcessingResult.TerminalFailure
        }
    }

    private suspend fun processCalls(messages: List<NotificationExtensionLogicMessage>): Boolean {
        val callEvents = mutableListOf<NotificationExtensionCallEvent>()
        var callMetadataMissing = false
        for (message in messages) {
            if (message.candidate?.kind == NotificationExtensionLogicContentKind.CALLING) {
                val callEvent = bridge.resolveCallEvent(message)
                if (callEvent == null) {
                    callMetadataMissing = true
                } else {
                    callEvents += callEvent.toExtensionCallEvent()
                }
            }
        }
        return when {
            callMetadataMissing -> true
            callEvents.isEmpty() -> false
            else -> when (
                runCatching { callProcessor.process(selfUserId, selfClientId, callEvents) }
                    .getOrDefault(NotificationExtensionCallProcessingStatus.RETRYABLE_FAILURE)
            ) {
                NotificationExtensionCallProcessingStatus.SUCCESS -> false
                NotificationExtensionCallProcessingStatus.RETRYABLE_FAILURE,
                NotificationExtensionCallProcessingStatus.TERMINAL_FAILURE -> true
            }
        }
    }

    private suspend fun stageChildren(
        event: StagedNotificationEvent,
        children: List<ReceiveChildWrite>
    ): NotificationExtensionLogicMaterializationStatus = when (val access = provider.get()) {
        is NotificationInboxStoreAccessResult.Opened -> when (
            val staged = access.store.stageReceiveChildren(
                ReceiveChildrenWrite(
                    scope = inboxScope,
                    parentServerEventId = event.key.serverEventId,
                    children = children
                )
            )
        ) {
            is ReceiveChildrenStageResult.Stored -> NotificationExtensionLogicMaterializationStatus.DURABLE
            ReceiveChildrenStageResult.ParentMissing,
            ReceiveChildrenStageResult.IntegrityConflict ->
                NotificationExtensionLogicMaterializationStatus.TERMINAL_FAILURE

            is ReceiveChildrenStageResult.StorageFailure -> if (staged.reason.isRetryableOpenFailure()) {
                NotificationExtensionLogicMaterializationStatus.RETRYABLE_FAILURE
            } else {
                NotificationExtensionLogicMaterializationStatus.TERMINAL_FAILURE
            }
        }

        NotificationInboxStoreAccessResult.RetryableFailure ->
            NotificationExtensionLogicMaterializationStatus.RETRYABLE_FAILURE

        NotificationInboxStoreAccessResult.TerminalFailure ->
            NotificationExtensionLogicMaterializationStatus.TERMINAL_FAILURE
    }
}

private class RealNotificationInboxStoreProvider(
    private val factory: EncryptedAppleNotificationInboxFactory
) : NotificationInboxStoreProvider {
    private var opened: NotificationInboxStoreAccessResult? = null

    override suspend fun get(): NotificationInboxStoreAccessResult {
        opened?.let { return it }
        val result = when (val open = factory.open()) {
            is EncryptedNotificationInboxOpenResult.Opened -> NotificationInboxStoreAccessResult.Opened(open.store)
            is EncryptedNotificationInboxOpenResult.Failure -> if (open.reason.isRetryableOpenFailure()) {
                NotificationInboxStoreAccessResult.RetryableFailure
            } else {
                NotificationInboxStoreAccessResult.TerminalFailure
            }
        }
        opened = result
        return result
    }

    override fun close() {
        (opened as? NotificationInboxStoreAccessResult.Opened)?.store?.close()
        opened = null
    }
}

private fun NotificationInboxFailure.isRetryableOpenFailure(): Boolean = when (this) {
    NotificationInboxFailure.STORAGE_UNAVAILABLE,
    NotificationInboxFailure.CLOSED,
    NotificationInboxFailure.UNEXPECTED_PLATFORM_FAILURE -> true

    NotificationInboxFailure.INVALID_INPUT,
    NotificationInboxFailure.CONFIGURED_LIMIT_EXCEEDED,
    NotificationInboxFailure.INCOMPATIBLE_SCHEMA,
    NotificationInboxFailure.CORRUPT_STATE,
    NotificationInboxFailure.ACCOUNT_NOT_ACTIVE,
    NotificationInboxFailure.ACCOUNT_TOMBSTONED,
    NotificationInboxFailure.CURSOR_CUTOVER_REQUIRED,
    NotificationInboxFailure.CURSOR_RECOVERY_REQUIRED -> false
}

private fun NotificationExtensionLogicMessage.toReceiveChild(
    scope: InboxScope,
    requiresForeground: Boolean
): ReceiveChildWrite = ReceiveChildWrite(
    scope = scope,
    parentServerEventId = eventId,
    itemIndex = itemIndex,
    idempotencyKey = candidate?.messageId
        ?.takeIf(String::isNotBlank)
        ?.let(::protocolMessageUidChildIdempotencyKey)
        ?: fallbackChildIdempotencyKey(eventId, itemIndex),
    conversationId = "$conversationId@$conversationDomain",
    senderId = "$senderId@$senderDomain",
    senderClientId = senderClientId,
    protocol = when (protocol) {
        com.wire.kalium.logic.notificationextension.NotificationExtensionLogicProtocol.PROTEUS ->
            ReceiveProtocol.PROTEUS

        com.wire.kalium.logic.notificationextension.NotificationExtensionLogicProtocol.MLS -> ReceiveProtocol.MLS
    },
    messageTimestampEpochMillis = timestampEpochMillis,
    decryptedProto = serializedContent,
    cryptoStateApplied = true,
    receiveClassification = ReceiveClassification.APPLICATION_MESSAGE,
    failureClassification = if (requiresForeground) "FOREGROUND_RECOVERY_REQUIRED" else null,
    decryptionState = realNotificationChildDecryptionState(),
    notificationState = NotificationState.SUPPRESSED,
    importState = ForegroundImportState.PENDING,
    retryCount = 0
)

internal fun realNotificationChildDecryptionState(): DecryptionState = DecryptionState.DECRYPTED

private fun NotificationExtensionLogicCallEvent.toExtensionCallEvent(): NotificationExtensionCallEvent =
    NotificationExtensionCallEvent(
        payload = payload,
        currentTimeSeconds = currentTimeSeconds,
        messageTimeSeconds = messageTimeSeconds,
        conversationId = conversationId,
        senderUserId = senderUserId,
        senderClientId = senderClientId,
        conversationType = conversationType
    )

private fun NotificationExtensionLogicTransportFrame.toDomainFrame(): NotificationTransportFrame = when (this) {
    is NotificationExtensionLogicTransportFrame.Event -> NotificationTransportFrame.Event(
        event = RawNotificationEvent(
            key = NotificationEventKey(eventId),
            rawEnvelope = rawEnvelope,
            isTransient = isTransient,
            cursor = cursor?.let(::NotificationSyncCursor)
        ),
        // The bounded engine acknowledges only after the encrypted store reports durable staging.
        deliveryTag = deliveryTag
    )

    is NotificationExtensionLogicTransportFrame.SynchronizationMarker ->
        NotificationTransportFrame.SynchronizationMarker(markerId, deliveryTag = deliveryTag)

    NotificationExtensionLogicTransportFrame.MissedNotification -> NotificationTransportFrame.MissedNotification
    NotificationExtensionLogicTransportFrame.Closed -> NotificationTransportFrame.Closed
    NotificationExtensionLogicTransportFrame.UnexpectedPayload -> NotificationTransportFrame.UnexpectedPayload
}

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

private fun RealNotificationExtensionRequest.toInboxLimits(): NotificationInboxLimits = NotificationInboxLimits(
    maxIdentifierUtf8Bytes = REAL_INBOX_MAX_IDENTIFIER_BYTES,
    maxCursorUtf8Bytes = REAL_INBOX_MAX_CURSOR_BYTES,
    maxReasonUtf8Bytes = REAL_INBOX_MAX_REASON_BYTES,
    maxRawEnvelopeBytes = maxRawEnvelopeBytes,
    maxDecryptedProtoBytes = maxRawEnvelopeBytes,
    maxBatchBlobBytes = maxRawEnvelopeBytesPerRun,
    maxRowsPerRead = maxEventsPerDrainBatch,
    maxChildrenPerEvent = maxEventsToStage,
    maxRetryCount = REAL_INBOX_MAX_RETRY_COUNT
)

private fun RealNotificationExtensionRequest.isValid(): Boolean =
    userId.isNotBlank() && absoluteDeadlineEpochMillis > 0L &&
            maxTransportFrames > 0 && maxEventsToStage > 0 && maxDrainBatches > 0 &&
            maxEventsPerDrainBatch > 0 && maxRawEnvelopeBytes > 0 &&
            maxRawEnvelopeBytesPerRun > 0 && maxDrainRawEnvelopeBytesPerRun > 0 &&
            deadlineSafetyMarginMillis >= 0L && maxRunDurationMillis > 0L

private fun RealNotificationExtensionConfiguration.isValid(): Boolean =
    kaliumRootPath.isNotBlank() && kaliumRootPath == sharedAppGroupRoot &&
            keychainServiceName.isNotBlank() && keychainAccessGroup.isNotBlank() && userAgent.isNotBlank()

private fun unavailableResult(reason: NotificationExtensionReason): RealNotificationExtensionResult =
    RealNotificationExtensionResult(
        status = NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE,
        reason = reason,
        summary = NotificationExtensionSummary.Empty,
        presentationDecision = RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK
    )

private fun lockUnavailableResult(): RealNotificationExtensionResult =
    RealNotificationExtensionResult(
        status = NotificationExtensionStatus.LOCK_UNAVAILABLE,
        reason = NotificationExtensionReason.LEASE_ACQUISITION_FAILED,
        summary = NotificationExtensionSummary.Empty,
        presentationDecision = RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK
    )

private fun rolloutDisabledRealResult(reason: NotificationExtensionReason): RealNotificationExtensionResult =
    RealNotificationExtensionResult(
        status = NotificationExtensionStatus.ROLLOUT_DISABLED,
        reason = reason,
        summary = NotificationExtensionSummary.Empty,
        presentationDecision = RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK
    )

internal fun cancelledResult(kind: Int): RealNotificationExtensionResult = RealNotificationExtensionResult(
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
    presentationDecision = RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK
)

internal fun NotificationExtensionStatus.toFailClosedPresentationDecision(): RealNotificationPresentationDecision =
    when (this) {
        NotificationExtensionStatus.COMPLETE,
        NotificationExtensionStatus.PARTIAL,
        NotificationExtensionStatus.ROLLOUT_DISABLED,
        NotificationExtensionStatus.LOCK_UNAVAILABLE,
        NotificationExtensionStatus.DEADLINE_REACHED,
        NotificationExtensionStatus.FOREGROUND_RECOVERY_REQUIRED,
        NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE,
        NotificationExtensionStatus.CANCELLED ->
            RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK
    }

internal fun accountLockRetryDelayMillis(attempt: Int): Long {
    val cappedAttempt = attempt.coerceIn(0, ACCOUNT_LOCK_MAX_BACKOFF_ATTEMPT)
    val exponential = ACCOUNT_LOCK_INITIAL_RETRY_MILLIS shl cappedAttempt
    val capped = exponential.coerceAtMost(ACCOUNT_LOCK_MAX_RETRY_MILLIS)
    val jitterBound = (capped / ACCOUNT_LOCK_JITTER_DIVISOR).coerceAtLeast(1L)
    return capped - Random.nextLong(jitterBound + 1L)
}

internal fun hasAccountLockRetryBudget(
    absoluteDeadlineEpochMillis: Long,
    safetyMarginMillis: Long,
    nowEpochMillis: Long,
    retryDelayMillis: Long
): Boolean = absoluteDeadlineEpochMillis - safetyMarginMillis - nowEpochMillis > retryDelayMillis

internal const val REAL_NOT_CANCELLED = 0
internal const val REAL_CANCELLED_BY_HOST = 1
internal const val REAL_CANCELLED_FOR_EXPIRATION = 2
private const val REAL_BEGIN_AVAILABLE = 0
private const val REAL_BEGIN_CLAIMED = 1
private const val REAL_COMPLETION_PENDING = 0
private const val REAL_COMPLETION_DELIVERED = 1
private const val ACCOUNT_LOCK_INITIAL_RETRY_MILLIS = 20L
private const val ACCOUNT_LOCK_MAX_RETRY_MILLIS = 250L
private const val ACCOUNT_LOCK_MAX_BACKOFF_ATTEMPT = 4
private const val ACCOUNT_LOCK_JITTER_DIVISOR = 4L
private const val REAL_INBOX_MAX_IDENTIFIER_BYTES = 1_024
private const val REAL_INBOX_MAX_CURSOR_BYTES = 1_024
private const val REAL_INBOX_MAX_REASON_BYTES = 256
private const val REAL_INBOX_MAX_RETRY_COUNT = 3
private val RETAINED_UNSAFE_ACCOUNT_LOCKS =
    AtomicReference<List<com.wire.kalium.logic.notificationextension.MainAppNotificationExtensionProcessLockResult>>(
        emptyList()
    )
private val REAL_IMPLEMENTATION_SATISFIED_GATES = setOf(
    NotificationExtensionProductionGate.VALIDATED_APP_GROUP_ROOT,
    NotificationExtensionProductionGate.ENCRYPTED_HANDOFF_STORAGE,
    NotificationExtensionProductionGate.SHARED_KEYCHAIN_AUTHENTICATION,
    NotificationExtensionProductionGate.RAW_EVENT_TRANSPORT_CAPTURE,
    NotificationExtensionProductionGate.RECEIVE_ONLY_CRYPTO_ASSEMBLY,
    NotificationExtensionProductionGate.NOTIFICATION_AVS_SWIFT_BRIDGE,
    NotificationExtensionProductionGate.BOUNDED_STORAGE_ENFORCEMENT
)
private val EXTERNALLY_VERIFIABLE_REAL_GATES = setOf(
    NotificationExtensionProductionGate.SIGNED_APP_AND_NSE_ENTITLEMENTS,
    NotificationExtensionProductionGate.PHYSICAL_DEVICE_VALIDATION,
    NotificationExtensionProductionGate.FOREGROUND_CURSOR_CUTOVER,
    NotificationExtensionProductionGate.ACCOUNT_REMOVAL_TOMBSTONE,
    NotificationExtensionProductionGate.GLOBAL_RECOVERY_FOREGROUND_ACK,
    NotificationExtensionProductionGate.PHYSICAL_DEVICE_BUDGET_APPROVAL,
    NotificationExtensionProductionGate.NATIVE_ROLLOUT_CONTROL_OWNERSHIP,
    NotificationExtensionProductionGate.APPROVED_GENERIC_FALLBACK_AND_REPLACEMENT,
    NotificationExtensionProductionGate.PRIVACY_DIAGNOSTICS_RETENTION_AND_EXPORT,
    NotificationExtensionProductionGate.CURSOR_CUTOVER_AND_DOWNGRADE_RELEASE,
    NotificationExtensionProductionGate.ROLLOUT_STOP_CONDITIONS_APPROVAL
)
private val EXTERNALLY_VERIFIABLE_REAL_GATE_MASK =
    EXTERNALLY_VERIFIABLE_REAL_GATES.fold(0L) { mask, gate -> mask or gate.bitMask }

internal fun claimRealNotificationExtensionBegin(beginState: AtomicInt): Boolean =
    beginState.compareAndSet(REAL_BEGIN_AVAILABLE, REAL_BEGIN_CLAIMED)
