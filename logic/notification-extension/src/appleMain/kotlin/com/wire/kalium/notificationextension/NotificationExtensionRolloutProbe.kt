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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.AtomicInt

/** Scalar-only result from the disposable M10 rollout and observation probe. */
@Suppress("LongParameterList")
public data class NotificationExtensionRolloutProbeResult(
    public val passed: Boolean,
    public val missingFailClosed: Boolean,
    public val unsupportedFailClosed: Boolean,
    public val staleFailClosed: Boolean,
    public val killSwitchFailClosed: Boolean,
    public val disabledFailClosed: Boolean,
    public val excludedFailClosed: Boolean,
    public val deniedRuntimeCalls: Int,
    public val eligibleRuntimeCalls: Int,
    public val observerFailureNonFatal: Boolean,
    public val observationPayloadFree: Boolean,
    public val completionAtMostOnce: Boolean,
    public val productionStillUnavailable: Boolean,
    public val detail: String
)

/**
 * Local synthetic evidence only. It does not fetch a flag, derive a cohort, or export diagnostics.
 */
public class NotificationExtensionRolloutProbe {
    public suspend fun run(): NotificationExtensionRolloutProbeResult = runCatching {
        execute()
    }.getOrElse {
        failedResult()
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun execute(): NotificationExtensionRolloutProbeResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val runtimeCalls = AtomicInt(0)
        val runtime = NotificationExtensionRuntime {
            runtimeCalls.fetchAndAdd(1)
            successfulSyntheticResult()
        }

        val missing = invoke(runtime, NotificationExtensionRolloutControl.Unavailable)
        val unsupported = invoke(runtime, enabledControl(now).copy(contractVersion = Int.MAX_VALUE))
        val stale = invoke(
            runtime,
            enabledControl(now).copy(
                issuedAtEpochMillis = now - SYNTHETIC_POLICY_VALIDITY_MILLIS,
                expiresAtEpochMillis = now - 1L
            )
        )
        val killSwitch = invoke(
            runtime,
            enabledControl(now).copy(
                contractVersion = Int.MAX_VALUE,
                killSwitchState = NotificationExtensionKillSwitchState.STOP,
                stopReason = NotificationExtensionRolloutStopReason.PRIVACY_OR_SECURITY
            )
        )
        val disabled = invoke(
            runtime,
            enabledControl(now).copy(featureState = NotificationExtensionFeatureState.DISABLED)
        )
        val excluded = invoke(
            runtime,
            enabledControl(now).copy(cohortDecision = NotificationExtensionCohortDecision.EXCLUDED)
        )
        val deniedRuntimeCalls = runtimeCalls.load()
        val eligible = invoke(runtime, enabledControl(now))
        val eligibleRuntimeCalls = runtimeCalls.load() - deniedRuntimeCalls

        val missingFailClosed = missing.matchesUnavailable(NotificationExtensionReason.ROLLOUT_CONTROL_UNAVAILABLE)
        val unsupportedFailClosed = unsupported.matchesUnavailable(
            NotificationExtensionReason.ROLLOUT_CONTROL_VERSION_UNSUPPORTED
        )
        val staleFailClosed = stale.matchesUnavailable(NotificationExtensionReason.ROLLOUT_CONTROL_STALE)
        val killSwitchFailClosed = killSwitch.matchesDisabled(NotificationExtensionReason.ROLLOUT_KILL_SWITCH)
        val disabledFailClosed = disabled.matchesDisabled(NotificationExtensionReason.ROLLOUT_FEATURE_DISABLED)
        val excludedFailClosed = excluded.matchesDisabled(NotificationExtensionReason.ROLLOUT_COHORT_EXCLUDED)
        val allInvocations = listOf(missing, unsupported, stale, killSwitch, disabled, excluded, eligible)
        val observerFailureNonFatal = allInvocations.all { it.observerCount == 1 && it.result != null }
        val completionAtMostOnce = allInvocations.all { it.completionCount == 1 }
        val observationPayloadFree = allInvocations.all { evidence ->
            val observation = evidence.observation ?: return@all false
            val rendered = observation.toString()
            observation.contractVersion == NOTIFICATION_EXTENSION_OBSERVATION_CONTRACT_VERSION &&
                    observation.opaqueCorrelationId == SYNTHETIC_CORRELATION_ID &&
                    !rendered.contains(PROBE_ACCOUNT_ID) && !rendered.contains(PROBE_CLIENT_ID) &&
                    !rendered.contains(PROBE_MARKER_ID) && !rendered.contains(SYNTHETIC_CORRELATION_ID) &&
                    !rendered.contains(SYNTHETIC_PAYLOAD_SENTINEL)
        } && eligible.observation?.eventsInserted == NotificationExtensionCountBucket.TWO_TO_FIVE

        val allResponsibilities = NotificationExtensionHostIntegrationReadiness(allHostResponsibilityMask)
        val construction = NotificationExtensionFactory.createProduction(
            NotificationExtensionHostConfiguration(
                sharedAppGroupRoot = "/synthetic/not-accessed",
                hostIntegrationReadiness = allResponsibilities
            )
        )
        val productionStillUnavailable = !construction.isAvailable &&
                construction.isBlockedBy(NotificationExtensionProductionGate.NATIVE_ROLLOUT_CONTROL_OWNERSHIP) &&
                construction.missingHostResponsibilityMask == 0L

        val passed = missingFailClosed && unsupportedFailClosed && staleFailClosed && killSwitchFailClosed &&
                disabledFailClosed && excludedFailClosed && deniedRuntimeCalls == 0 && eligibleRuntimeCalls == 1 &&
                observerFailureNonFatal && observationPayloadFree && completionAtMostOnce &&
                productionStillUnavailable && eligible.result?.status == NotificationExtensionStatus.COMPLETE
        return NotificationExtensionRolloutProbeResult(
            passed = passed,
            missingFailClosed = missingFailClosed,
            unsupportedFailClosed = unsupportedFailClosed,
            staleFailClosed = staleFailClosed,
            killSwitchFailClosed = killSwitchFailClosed,
            disabledFailClosed = disabledFailClosed,
            excludedFailClosed = excludedFailClosed,
            deniedRuntimeCalls = deniedRuntimeCalls,
            eligibleRuntimeCalls = eligibleRuntimeCalls,
            observerFailureNonFatal = observerFailureNonFatal,
            observationPayloadFree = observationPayloadFree,
            completionAtMostOnce = completionAtMostOnce,
            productionStillUnavailable = productionStillUnavailable,
            detail = "synthetic=true; fixedObservation=true; diagnosticsExport=false; production=false"
        )
    }

    private suspend fun invoke(
        runtime: NotificationExtensionRuntime,
        control: NotificationExtensionRolloutControl
    ): InvocationEvidence {
        val completionCount = AtomicInt(0)
        val completion = CompletableDeferred<NotificationExtensionResult>()
        val observer = RecordingThrowingObserver()
        val component = NotificationExtension(runtime)
        val handle = component.beginObserved(
            request = syntheticRequest(control),
            observer = observer
        ) { result ->
            completionCount.fetchAndAdd(1)
            completion.complete(result)
        }
        val result = completion.await()
        observer.awaitObserved()
        handle.cancel()
        handle.cancelForExpiration()
        yield()
        component.close()
        return InvocationEvidence(
            result = result,
            completionCount = completionCount.load(),
            observerCount = observer.count,
            observation = observer.observation
        )
    }
}

private class RecordingThrowingObserver : NotificationExtensionObserver {
    private val observed = CompletableDeferred<Unit>()
    var count: Int = 0
    var observation: NotificationExtensionObservation? = null

    override fun observe(observation: NotificationExtensionObservation) {
        count += 1
        this.observation = observation
        observed.complete(Unit)
        error("synthetic-observer-failure")
    }

    suspend fun awaitObserved() {
        observed.await()
    }
}

private data class InvocationEvidence(
    val result: NotificationExtensionResult?,
    val completionCount: Int,
    val observerCount: Int,
    val observation: NotificationExtensionObservation?
) {
    fun matchesDisabled(reason: NotificationExtensionReason): Boolean =
        result?.status == NotificationExtensionStatus.ROLLOUT_DISABLED && result.reason == reason &&
                result.shouldUsePrivacyPreservingFallback && result.summary == NotificationExtensionSummary.Empty

    fun matchesUnavailable(reason: NotificationExtensionReason): Boolean =
        result?.status == NotificationExtensionStatus.CONFIGURATION_UNAVAILABLE && result.reason == reason &&
                result.shouldUsePrivacyPreservingFallback && result.summary == NotificationExtensionSummary.Empty
}

private fun successfulSyntheticResult(): NotificationExtensionResult = NotificationExtensionResult(
    status = NotificationExtensionStatus.COMPLETE,
    reason = NotificationExtensionReason.NONE,
    summary = NotificationExtensionSummary(
        transportFramesReceived = 3,
        eventsInserted = 2,
        eventsAlreadyStaged = 1,
        transportAcksAcceptedByLocalWriter = 2,
        eventsReceiveMaterialized = 2,
        drainBatchesRead = 1,
        transportRawEnvelopeBytesReceived = SYNTHETIC_EXACT_BYTE_COUNT,
        drainRawEnvelopeBytesRead = SYNTHETIC_EXACT_BYTE_COUNT
    ),
    shouldUsePrivacyPreservingFallback = true
)

private fun syntheticRequest(control: NotificationExtensionRolloutControl): NotificationExtensionRequest =
    NotificationExtensionRequest(
        accountId = PROBE_ACCOUNT_ID,
        clientId = PROBE_CLIENT_ID,
        markerId = PROBE_MARKER_ID,
        absoluteDeadlineEpochMillis = Clock.System.now().toEpochMilliseconds() + SYNTHETIC_DEADLINE_MILLIS,
        rolloutControl = control,
        opaqueCorrelationId = SYNTHETIC_CORRELATION_ID
    )

private fun enabledControl(nowEpochMillis: Long): NotificationExtensionRolloutControl =
    NotificationExtensionRolloutControl(
        contractVersion = NOTIFICATION_EXTENSION_ROLLOUT_CONTRACT_VERSION,
        revision = 1L,
        issuedAtEpochMillis = nowEpochMillis - 1L,
        expiresAtEpochMillis = nowEpochMillis + SYNTHETIC_POLICY_VALIDITY_MILLIS,
        featureState = NotificationExtensionFeatureState.ENABLED,
        killSwitchState = NotificationExtensionKillSwitchState.ALLOW,
        cohortDecision = NotificationExtensionCohortDecision.INCLUDED,
        stopReason = NotificationExtensionRolloutStopReason.NONE
    )

private fun failedResult(): NotificationExtensionRolloutProbeResult = NotificationExtensionRolloutProbeResult(
    passed = false,
    missingFailClosed = false,
    unsupportedFailClosed = false,
    staleFailClosed = false,
    killSwitchFailClosed = false,
    disabledFailClosed = false,
    excludedFailClosed = false,
    deniedRuntimeCalls = -1,
    eligibleRuntimeCalls = -1,
    observerFailureNonFatal = false,
    observationPayloadFree = false,
    completionAtMostOnce = false,
    productionStillUnavailable = false,
    detail = "synthetic=true; phase=failed; code=rollout-probe-failure"
)

private const val PROBE_ACCOUNT_ID = "synthetic-m10-account"
private const val PROBE_CLIENT_ID = "synthetic-m10-client"
private const val PROBE_MARKER_ID = "synthetic-m10-marker"
private const val SYNTHETIC_CORRELATION_ID = "00000000-0000-4000-8000-000000000010"
private const val SYNTHETIC_PAYLOAD_SENTINEL = "payload-must-never-appear"
private const val SYNTHETIC_EXACT_BYTE_COUNT = 12_345L
private const val SYNTHETIC_DEADLINE_MILLIS = 10_000L
private const val SYNTHETIC_POLICY_VALIDITY_MILLIS = 60_000L
