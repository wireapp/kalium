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

package com.wire.kalium.notificationextension

/** Version of the host-to-framework rollout decision contract. */
public const val NOTIFICATION_EXTENSION_ROLLOUT_CONTRACT_VERSION: Int = 1

/** Version of the bounded, payload-excluding completion observation. */
public const val NOTIFICATION_EXTENSION_OBSERVATION_CONTRACT_VERSION: Int = 1

/** Host feature-flag value. Missing or unreadable state must be represented as [UNAVAILABLE]. */
public enum class NotificationExtensionFeatureState {
    ENABLED,
    DISABLED,
    UNAVAILABLE
}

/** Host kill-switch value. [STOP] always prevents the runtime from being entered. */
public enum class NotificationExtensionKillSwitchState {
    ALLOW,
    STOP,
    UNAVAILABLE
}

/** Host-owned cohort evaluation. Kalium never derives a cohort from account or message data. */
public enum class NotificationExtensionCohortDecision {
    INCLUDED,
    EXCLUDED,
    UNAVAILABLE
}

/**
 * Coarse rollout stop classification. It contains no exception text or identifiers.
 *
 * Product owners define thresholds externally. Kalium only accepts the already-evaluated stop
 * decision and does not invent a backend, telemetry system, or product SLO.
 */
public enum class NotificationExtensionRolloutStopReason {
    NONE,
    PRIVACY_OR_SECURITY,
    CRASH_OR_MEMORY_PRESSURE,
    DEADLINE_MARGIN,
    STORAGE_INTEGRITY,
    CURSOR_OR_IMPORT_INTEGRITY,
    CRYPTO_STATE_INTEGRITY,
    BACKEND_DELIVERY_OR_GAP,
    NOTIFICATION_DUPLICATION_OR_DISCLOSURE,
    OTHER_APPROVED_STOP
}

/**
 * Pure, versioned decision supplied by the native host before one invocation.
 *
 * The unavailable companion value is deliberately fail closed. A stop reason is valid only while
 * the kill switch is [NotificationExtensionKillSwitchState.STOP].
 */
public data class NotificationExtensionRolloutControl(
    public val contractVersion: Int,
    public val revision: Long,
    public val issuedAtEpochMillis: Long,
    public val expiresAtEpochMillis: Long,
    public val featureState: NotificationExtensionFeatureState,
    public val killSwitchState: NotificationExtensionKillSwitchState,
    public val cohortDecision: NotificationExtensionCohortDecision,
    public val stopReason: NotificationExtensionRolloutStopReason
) {
    override fun toString(): String = "NotificationExtensionRolloutControl(redacted)"

    public companion object {
        public val Unavailable: NotificationExtensionRolloutControl = NotificationExtensionRolloutControl(
            contractVersion = NOTIFICATION_EXTENSION_ROLLOUT_CONTRACT_VERSION,
            revision = 0L,
            issuedAtEpochMillis = 0L,
            expiresAtEpochMillis = 0L,
            featureState = NotificationExtensionFeatureState.UNAVAILABLE,
            killSwitchState = NotificationExtensionKillSwitchState.UNAVAILABLE,
            cohortDecision = NotificationExtensionCohortDecision.UNAVAILABLE,
            stopReason = NotificationExtensionRolloutStopReason.NONE
        )
    }
}

/** Stable elapsed-time buckets; no high-resolution timing is exported. */
public enum class NotificationExtensionDurationBucket {
    UNDER_100_MILLIS,
    UNDER_500_MILLIS,
    UNDER_2_SECONDS,
    UNDER_5_SECONDS,
    UNDER_15_SECONDS,
    THIRTY_SECONDS_OR_LESS,
    OVER_30_SECONDS
}

/** Stable remaining-deadline buckets evaluated after the completion callback returns. */
public enum class NotificationExtensionDeadlineMarginBucket {
    EXPIRED,
    UNDER_500_MILLIS,
    UNDER_2_SECONDS,
    UNDER_5_SECONDS,
    FIVE_SECONDS_OR_MORE
}

/** Bounded activity buckets. Exact result counters remain local and are not copied to diagnostics. */
public enum class NotificationExtensionCountBucket {
    ZERO,
    ONE,
    TWO_TO_FIVE,
    SIX_TO_TWENTY,
    TWENTY_ONE_TO_ONE_HUNDRED,
    OVER_ONE_HUNDRED
}

/**
 * One bounded, payload-excluding completion observation.
 *
 * It deliberately omits byte counts because per-message byte sizes can become a content side
 * channel. [opaqueCorrelationId] is copied only when it is a lowercase canonical UUID-v4 string.
 * Shape validation is not a privacy guarantee: the native owner must generate it randomly, keep it
 * non-PII, and never derive it from account, user, conversation, notification, or message data.
 */
@Suppress("LongParameterList")
public data class NotificationExtensionObservation(
    public val contractVersion: Int,
    public val opaqueCorrelationId: String?,
    public val status: NotificationExtensionStatus,
    public val reason: NotificationExtensionReason,
    public val duration: NotificationExtensionDurationBucket,
    public val deadlineMargin: NotificationExtensionDeadlineMarginBucket,
    public val transportFramesReceived: NotificationExtensionCountBucket,
    public val eventsInserted: NotificationExtensionCountBucket,
    public val eventsAlreadyStaged: NotificationExtensionCountBucket,
    public val transportAcksAcceptedByLocalWriter: NotificationExtensionCountBucket,
    public val eventsReceiveMaterialized: NotificationExtensionCountBucket,
    public val drainBatchesRead: NotificationExtensionCountBucket
) {
    override fun toString(): String =
        "NotificationExtensionObservation(status=$status, reason=$reason, duration=$duration, " +
                "deadlineMargin=$deadlineMargin, countsOnly=true, correlationPresent=${opaqueCorrelationId != null})"
}

/**
 * Optional non-blocking host observer invoked at most once after the completion callback.
 *
 * Exceptions are swallowed. The framework emits exactly one fixed-shape observation per run, so
 * observer volume and record size are bounded. Diagnostics retention and export happen outside
 * this callback and remain native-app responsibilities.
 */
public fun interface NotificationExtensionObserver {
    public fun observe(observation: NotificationExtensionObservation)
}

/** External integration work that Kalium cannot truthfully implement inside this repository. */
public enum class NotificationExtensionHostResponsibility(public val bitMask: Long) {
    APPROVED_GENERIC_MESSAGE_FALLBACK(1L shl APPROVED_GENERIC_MESSAGE_FALLBACK_BIT),
    APPROVED_GENERIC_CALL_FALLBACK(1L shl APPROVED_GENERIC_CALL_FALLBACK_BIT),
    FEATURE_FLAG_SOURCE(1L shl FEATURE_FLAG_SOURCE_BIT),
    KILL_SWITCH_SOURCE(1L shl KILL_SWITCH_SOURCE_BIT),
    COHORT_DECISION_OWNER(1L shl COHORT_DECISION_OWNER_BIT),
    DIAGNOSTICS_RETENTION_OWNER(1L shl DIAGNOSTICS_RETENTION_OWNER_BIT),
    DIAGNOSTICS_EXPORT_OWNER(1L shl DIAGNOSTICS_EXPORT_OWNER_BIT),
    NOTIFICATION_REPLACEMENT_IDENTIFIER_OWNER(1L shl NOTIFICATION_REPLACEMENT_IDENTIFIER_OWNER_BIT),
    CURSOR_CUTOVER_OWNER(1L shl CURSOR_CUTOVER_OWNER_BIT),
    DOWNGRADE_AND_ROLLBACK_OWNER(1L shl DOWNGRADE_AND_ROLLBACK_OWNER_BIT),
    ROLLOUT_STOP_CONDITION_OWNER(1L shl ROLLOUT_STOP_CONDITION_OWNER_BIT)
}

/** Native host declaration used only to report missing production ownership. */
public data class NotificationExtensionHostIntegrationReadiness(
    public val fulfilledResponsibilityMask: Long
) {
    override fun toString(): String = "NotificationExtensionHostIntegrationReadiness(redacted)"

    public fun includes(responsibility: NotificationExtensionHostResponsibility): Boolean =
        fulfilledResponsibilityMask and responsibility.bitMask != NO_HOST_RESPONSIBILITIES

    public companion object {
        public val None: NotificationExtensionHostIntegrationReadiness =
            NotificationExtensionHostIntegrationReadiness(NO_HOST_RESPONSIBILITIES)
    }
}

internal sealed interface NotificationExtensionRolloutEvaluation {
    data object Enabled : NotificationExtensionRolloutEvaluation
    data class Disabled(val reason: NotificationExtensionReason) : NotificationExtensionRolloutEvaluation
    data class Unavailable(val reason: NotificationExtensionReason) : NotificationExtensionRolloutEvaluation
}

@Suppress("CyclomaticComplexMethod", "ReturnCount")
internal fun NotificationExtensionRolloutControl.evaluate(nowEpochMillis: Long): NotificationExtensionRolloutEvaluation {
    // A locally readable stop decision wins even if the remaining snapshot fields are old or
    // unknown. It can only reduce behavior and never makes an incompatible policy executable.
    if (killSwitchState == NotificationExtensionKillSwitchState.STOP) {
        return NotificationExtensionRolloutEvaluation.Disabled(NotificationExtensionReason.ROLLOUT_KILL_SWITCH)
    }
    if (contractVersion != NOTIFICATION_EXTENSION_ROLLOUT_CONTRACT_VERSION) {
        return NotificationExtensionRolloutEvaluation.Unavailable(
            NotificationExtensionReason.ROLLOUT_CONTROL_VERSION_UNSUPPORTED
        )
    }
    if (killSwitchState == NotificationExtensionKillSwitchState.UNAVAILABLE) {
        return NotificationExtensionRolloutEvaluation.Unavailable(
            NotificationExtensionReason.ROLLOUT_CONTROL_UNAVAILABLE
        )
    }
    val validityMillis = expiresAtEpochMillis - issuedAtEpochMillis
    if (hasInvalidValidity(nowEpochMillis, validityMillis)) {
        return NotificationExtensionRolloutEvaluation.Unavailable(NotificationExtensionReason.ROLLOUT_CONTROL_INVALID)
    }
    if (nowEpochMillis >= expiresAtEpochMillis) {
        return NotificationExtensionRolloutEvaluation.Unavailable(NotificationExtensionReason.ROLLOUT_CONTROL_STALE)
    }
    return when (featureState) {
        NotificationExtensionFeatureState.DISABLED ->
            NotificationExtensionRolloutEvaluation.Disabled(NotificationExtensionReason.ROLLOUT_FEATURE_DISABLED)
        NotificationExtensionFeatureState.UNAVAILABLE ->
            NotificationExtensionRolloutEvaluation.Unavailable(NotificationExtensionReason.ROLLOUT_CONTROL_UNAVAILABLE)
        NotificationExtensionFeatureState.ENABLED -> when (cohortDecision) {
            NotificationExtensionCohortDecision.INCLUDED -> NotificationExtensionRolloutEvaluation.Enabled
            NotificationExtensionCohortDecision.EXCLUDED ->
                NotificationExtensionRolloutEvaluation.Disabled(NotificationExtensionReason.ROLLOUT_COHORT_EXCLUDED)
            NotificationExtensionCohortDecision.UNAVAILABLE ->
                NotificationExtensionRolloutEvaluation.Unavailable(
                    NotificationExtensionReason.ROLLOUT_CONTROL_UNAVAILABLE
                )
        }
    }
}

private fun NotificationExtensionRolloutControl.hasInvalidValidity(
    nowEpochMillis: Long,
    validityMillis: Long
): Boolean {
    val revisionOrTimesInvalid = revision <= 0L || issuedAtEpochMillis <= 0L ||
            expiresAtEpochMillis <= issuedAtEpochMillis
    val validityWindowInvalid = validityMillis !in 1L..MAX_ROLLOUT_VALIDITY_MILLIS ||
            nowEpochMillis < issuedAtEpochMillis
    return revisionOrTimesInvalid || validityWindowInvalid || stopReason != NotificationExtensionRolloutStopReason.NONE
}

internal fun String?.validatedOpaqueCorrelationId(): String? {
    val isValid = this != null && length == UUID_V4_CANONICAL_LENGTH && indices.all { index ->
        when (index) {
            UUID_FIRST_HYPHEN_INDEX,
            UUID_SECOND_HYPHEN_INDEX,
            UUID_THIRD_HYPHEN_INDEX,
            UUID_FOURTH_HYPHEN_INDEX -> this[index] == UUID_HYPHEN
            UUID_VERSION_INDEX -> this[index] == UUID_V4_VERSION
            UUID_VARIANT_INDEX -> this[index] in UUID_V4_VARIANTS
            else -> this[index].isLowercaseHexDigit()
        }
    }
    return if (isValid) this else null
}

private fun Char.isLowercaseHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f'

internal fun durationBucket(elapsedMillis: Long): NotificationExtensionDurationBucket = when {
    elapsedMillis < DURATION_100_MILLIS -> NotificationExtensionDurationBucket.UNDER_100_MILLIS
    elapsedMillis < DURATION_500_MILLIS -> NotificationExtensionDurationBucket.UNDER_500_MILLIS
    elapsedMillis < DURATION_2_SECONDS -> NotificationExtensionDurationBucket.UNDER_2_SECONDS
    elapsedMillis < DURATION_5_SECONDS -> NotificationExtensionDurationBucket.UNDER_5_SECONDS
    elapsedMillis < DURATION_15_SECONDS -> NotificationExtensionDurationBucket.UNDER_15_SECONDS
    elapsedMillis <= DURATION_30_SECONDS -> NotificationExtensionDurationBucket.THIRTY_SECONDS_OR_LESS
    else -> NotificationExtensionDurationBucket.OVER_30_SECONDS
}

internal fun deadlineMarginBucket(marginMillis: Long): NotificationExtensionDeadlineMarginBucket = when {
    marginMillis <= 0L -> NotificationExtensionDeadlineMarginBucket.EXPIRED
    marginMillis < DURATION_500_MILLIS -> NotificationExtensionDeadlineMarginBucket.UNDER_500_MILLIS
    marginMillis < DURATION_2_SECONDS -> NotificationExtensionDeadlineMarginBucket.UNDER_2_SECONDS
    marginMillis < DURATION_5_SECONDS -> NotificationExtensionDeadlineMarginBucket.UNDER_5_SECONDS
    else -> NotificationExtensionDeadlineMarginBucket.FIVE_SECONDS_OR_MORE
}

internal fun countBucket(count: Int): NotificationExtensionCountBucket = when (count) {
    in Int.MIN_VALUE..0 -> NotificationExtensionCountBucket.ZERO
    1 -> NotificationExtensionCountBucket.ONE
    in MIN_TWO_COUNT..MAX_FIVE_COUNT -> NotificationExtensionCountBucket.TWO_TO_FIVE
    in MIN_SIX_COUNT..MAX_TWENTY_COUNT -> NotificationExtensionCountBucket.SIX_TO_TWENTY
    in MIN_TWENTY_ONE_COUNT..MAX_ONE_HUNDRED_COUNT -> NotificationExtensionCountBucket.TWENTY_ONE_TO_ONE_HUNDRED
    else -> NotificationExtensionCountBucket.OVER_ONE_HUNDRED
}

internal val allHostResponsibilityMask: Long = NotificationExtensionHostResponsibility.entries.fold(
    NO_HOST_RESPONSIBILITIES
) { mask, responsibility -> mask or responsibility.bitMask }

private const val UUID_V4_CANONICAL_LENGTH = 36
private const val UUID_FIRST_HYPHEN_INDEX = 8
private const val UUID_SECOND_HYPHEN_INDEX = 13
private const val UUID_VERSION_INDEX = 14
private const val UUID_THIRD_HYPHEN_INDEX = 18
private const val UUID_VARIANT_INDEX = 19
private const val UUID_FOURTH_HYPHEN_INDEX = 23
private const val UUID_HYPHEN = '-'
private const val UUID_V4_VERSION = '4'
private const val UUID_V4_VARIANTS = "89ab"
private const val MAX_ROLLOUT_VALIDITY_MILLIS = 24L * 60L * 60L * 1_000L
private const val DURATION_100_MILLIS = 100L
private const val DURATION_500_MILLIS = 500L
private const val DURATION_2_SECONDS = 2_000L
private const val DURATION_5_SECONDS = 5_000L
private const val DURATION_15_SECONDS = 15_000L
private const val DURATION_30_SECONDS = 30_000L
private const val MIN_TWO_COUNT = 2
private const val MAX_FIVE_COUNT = 5
private const val MIN_SIX_COUNT = 6
private const val MAX_TWENTY_COUNT = 20
private const val MIN_TWENTY_ONE_COUNT = 21
private const val MAX_ONE_HUNDRED_COUNT = 100
private const val NO_HOST_RESPONSIBILITIES = 0L
private const val APPROVED_GENERIC_MESSAGE_FALLBACK_BIT = 0
private const val APPROVED_GENERIC_CALL_FALLBACK_BIT = 1
private const val FEATURE_FLAG_SOURCE_BIT = 2
private const val KILL_SWITCH_SOURCE_BIT = 3
private const val COHORT_DECISION_OWNER_BIT = 4
private const val DIAGNOSTICS_RETENTION_OWNER_BIT = 5
private const val DIAGNOSTICS_EXPORT_OWNER_BIT = 6
private const val NOTIFICATION_REPLACEMENT_IDENTIFIER_OWNER_BIT = 7
private const val CURSOR_CUTOVER_OWNER_BIT = 8
private const val DOWNGRADE_AND_ROLLBACK_OWNER_BIT = 9
private const val ROLLOUT_STOP_CONDITION_OWNER_BIT = 10
