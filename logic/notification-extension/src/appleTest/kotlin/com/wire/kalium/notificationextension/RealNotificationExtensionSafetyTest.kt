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
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package com.wire.kalium.notificationextension

import com.wire.kalium.notificationinbox.EncryptedAppleNotificationInboxFactory
import com.wire.kalium.notificationinbox.EncryptedNotificationInboxOpenResult
import com.wire.kalium.notificationinbox.InboxScope
import com.wire.kalium.notificationinbox.DecryptionState
import com.wire.kalium.notificationinbox.NotificationInboxFailure
import com.wire.kalium.notificationinbox.NotificationInboxLimits
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RealNotificationExtensionSafetyTest {
    @Test
    fun givenRepeatedBeginClaim_whenStartingRealExtension_thenOnlyFirstRunIsAccepted() {
        val beginState = kotlin.concurrent.atomics.AtomicInt(0)

        assertTrue(claimRealNotificationExtensionBegin(beginState))
        assertFalse(claimRealNotificationExtensionBegin(beginState))
    }

    @Test
    fun givenUnsafeTeardown_whenExecuting_thenAccountLockIsRetained() = runBlocking {
        val timeline = mutableListOf<String>()

        val result = executeRetainingAccountLockOnUnsafeTeardown(
            execution = {
                throw UnsafeRealNotificationExtensionTeardown(
                    IllegalStateException("construction rollback cleanup failed")
                )
            },
            retainAccountLock = { timeline += "retained" },
            releaseAccountLock = { timeline += "released" }
        )

        assertEquals(NotificationExtensionReason.RUNTIME_FAILURE, result.reason)
        assertEquals(listOf("retained"), timeline)
    }

    @Test
    fun givenSafeConstructionFailure_whenExecuting_thenAccountLockIsReleased() = runBlocking {
        val timeline = mutableListOf<String>()

        val failure = runCatching {
            executeRetainingAccountLockOnUnsafeTeardown(
                execution = { throw IllegalStateException("construction failed after safe rollback") },
                retainAccountLock = { timeline += "retained" },
                releaseAccountLock = { timeline += "released" }
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("released"), timeline)
    }

    @Test
    fun givenRealMessageRequiresForegroundWork_whenBuildingChild_thenAppliedDecryptionStateRemainsSchemaValid() {
        assertEquals(DecryptionState.DECRYPTED, realNotificationChildDecryptionState())
    }

    @Test
    fun givenHostCancellation_whenHandleIsCancelled_thenJobAndResultAreCancelledWithFallback() {
        val job = Job()
        val cancellationKind = AtomicInt(REAL_NOT_CANCELLED)
        val handle = RealNotificationExtensionRunHandle(job, cancellationKind)

        handle.cancel()

        assertTrue(job.isCancelled)
        assertEquals(REAL_CANCELLED_BY_HOST, cancellationKind.load())
        val result = cancelledResult(cancellationKind.load())
        assertEquals(NotificationExtensionStatus.CANCELLED, result.status)
        assertEquals(RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK, result.presentationDecision)
    }

    @Test
    fun givenExpirationCancellation_whenHandleIsCancelled_thenDeadlineResultUsesFallback() {
        val job = Job()
        val cancellationKind = AtomicInt(REAL_NOT_CANCELLED)
        val handle = RealNotificationExtensionRunHandle(job, cancellationKind)

        handle.cancelForExpiration()

        assertTrue(job.isCancelled)
        assertEquals(REAL_CANCELLED_FOR_EXPIRATION, cancellationKind.load())
        val result = cancelledResult(cancellationKind.load())
        assertEquals(NotificationExtensionStatus.DEADLINE_REACHED, result.status)
        assertEquals(NotificationExtensionReason.DEADLINE, result.reason)
        assertEquals(RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK, result.presentationDecision)
    }

    @Test
    fun givenRetryWouldEnterSafetyMargin_whenCheckingLockBudget_thenRetryIsRejected() {
        assertFalse(
            hasAccountLockRetryBudget(
                absoluteDeadlineEpochMillis = 10_000L,
                safetyMarginMillis = 2_000L,
                nowEpochMillis = 7_900L,
                retryDelayMillis = 100L
            )
        )
    }

    @Test
    fun givenTimeBeforeSafetyMargin_whenCheckingLockBudget_thenRetryIsAllowed() {
        assertTrue(
            hasAccountLockRetryBudget(
                absoluteDeadlineEpochMillis = 10_000L,
                safetyMarginMillis = 2_000L,
                nowEpochMillis = 7_000L,
                retryDelayMillis = 100L
            )
        )
    }

    @Test
    fun givenAnyTerminalStatus_whenSelectingPresentation_thenOnlyFallbackIsReturned() {
        NotificationExtensionStatus.entries.forEach { status ->
            assertEquals(
                RealNotificationPresentationDecision.PRIVACY_PRESERVING_FALLBACK,
                status.toFailClosedPresentationDecision()
            )
        }
    }

    @Test
    fun givenManyAttempts_whenCalculatingJitteredRetry_thenDelayRemainsBounded() {
        repeat(100) {
            val delayMillis = accountLockRetryDelayMillis(Int.MAX_VALUE)
            assertTrue(delayMillis in 188L..250L)
        }
    }

    @Test
    fun givenProductionFactory_whenExternalEvidenceIsSupplied_thenOnlyAllowedExternalGatesClose() {
        assertEquals(
            EXTERNALLY_VERIFIABLE_REAL_GATE_MASK_FOR_TEST,
            RealNotificationExtensionProductionReadiness.SupportedExternalGateMask
        )
        val construction = RealNotificationExtensionFactory.createProduction(
            configuration = RealNotificationExtensionConfiguration(
                sharedAppGroupRoot = "/tmp/app-group",
                kaliumRootPath = "/tmp/app-group",
                keychainServiceName = "service",
                keychainAccessGroup = "group",
                userAgent = "agent"
            ),
            callProcessor = NotificationExtensionCallProcessor {
                    _, _, _ -> NotificationExtensionCallProcessingStatus.SUCCESS
            },
            readiness = RealNotificationExtensionProductionReadiness(
                externallyVerifiedGateMask = EXTERNALLY_VERIFIABLE_REAL_GATE_MASK_FOR_TEST,
                hostIntegrationReadiness = NotificationExtensionHostIntegrationReadiness(allHostResponsibilityMask)
            )
        )

        assertFalse(construction.isAvailable)
        assertTrue(construction.isConfigurationValid)
        PROVEN_REAL_GATES.forEach { assertFalse(construction.isBlockedBy(it)) }
        EXTERNALLY_VERIFIABLE_REAL_GATES_FOR_TEST.forEach { assertFalse(construction.isBlockedBy(it)) }
        CODE_OWNED_UNPROVEN_REAL_GATES.forEach { assertTrue(construction.isBlockedBy(it)) }
        assertEquals(0L, construction.missingHostResponsibilityMask)
        assertEquals(0L, construction.rejectedExternalGateClaimMask)
    }

    @Test
    fun givenMismatchedKaliumAndAppGroupRoots_whenCreatingProduction_thenConfigurationIsRejected() {
        val construction = RealNotificationExtensionFactory.createProduction(
            configuration = RealNotificationExtensionConfiguration(
                kaliumRootPath = "/tmp/other-root",
                sharedAppGroupRoot = "/tmp/app-group",
                keychainServiceName = "service",
                keychainAccessGroup = "group",
                userAgent = "agent"
            ),
            callProcessor = NotificationExtensionCallProcessor {
                    _, _, _ -> NotificationExtensionCallProcessingStatus.SUCCESS
            },
            readiness = RealNotificationExtensionProductionReadiness.None
        )

        assertFalse(construction.isAvailable)
        assertFalse(construction.isConfigurationValid)
    }

    @Test
    fun givenCrashAfterChildStageBeforeCryptoCommit_whenHostClaimsOrderingGate_thenClaimIsRejectedAndGateStaysBlocked() {
        val construction = RealNotificationExtensionFactory.createProduction(
            configuration = RealNotificationExtensionConfiguration(
                sharedAppGroupRoot = "/tmp/app-group",
                kaliumRootPath = "/tmp/app-group",
                keychainServiceName = "service",
                keychainAccessGroup = "group",
                userAgent = "agent"
            ),
            callProcessor = NotificationExtensionCallProcessor {
                    _, _, _ -> NotificationExtensionCallProcessingStatus.SUCCESS
            },
            readiness = RealNotificationExtensionProductionReadiness(
                externallyVerifiedGateMask =
                    NotificationExtensionProductionGate.CORE_CRYPTO_HANDOFF_CRASH_ORDERING.bitMask,
                hostIntegrationReadiness = NotificationExtensionHostIntegrationReadiness(allHostResponsibilityMask)
            )
        )

        assertFalse(construction.isAvailable)
        assertTrue(
            construction.isBlockedBy(NotificationExtensionProductionGate.CORE_CRYPTO_HANDOFF_CRASH_ORDERING)
        )
        assertTrue(
            construction.rejectedExternalClaimFor(
                NotificationExtensionProductionGate.CORE_CRYPTO_HANDOFF_CRASH_ORDERING
            )
        )
    }

    @Test
    fun givenEncryptedInbox_whenCipherIsUnavailableOrPresent_thenPlaintextIsNeverAccepted() = runBlocking {
        val root = "${NSTemporaryDirectory().trimEnd('/')}/kalium-inbox-${Uuid.random()}"
        val factory = EncryptedAppleNotificationInboxFactory(
            sharedAppGroupRoot = root,
            scope = ENCRYPTION_TEST_SCOPE,
            key = CORRECT_TEST_KEY,
            limits = ENCRYPTION_TEST_LIMITS
        )
        try {
            when (val opened = factory.open()) {
                is EncryptedNotificationInboxOpenResult.Failure ->
                    assertTrue(
                        opened.reason == NotificationInboxFailure.INCOMPATIBLE_SCHEMA ||
                                opened.reason == NotificationInboxFailure.STORAGE_UNAVAILABLE
                    )
                is EncryptedNotificationInboxOpenResult.Opened -> {
                    opened.store.close()
                    val header = checkNotNull(NSData.dataWithContentsOfFile(factory.databaseFilePath))
                        .firstBytes(SQLITE_HEADER.length)
                        .decodeToString()
                    assertFalse(header.startsWith(SQLITE_HEADER))

                    val wrongKey = EncryptedAppleNotificationInboxFactory(
                        sharedAppGroupRoot = root,
                        scope = ENCRYPTION_TEST_SCOPE,
                        key = WRONG_TEST_KEY,
                        limits = ENCRYPTION_TEST_LIMITS
                    ).open()
                    assertTrue(wrongKey is EncryptedNotificationInboxOpenResult.Failure)
                }
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(root, error = null)
        }
    }
}

private fun NSData.firstBytes(count: Int): ByteArray {
    require(length.toLong() >= count)
    return ByteArray(count).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, count.toULong())
        }
    }
}

private val ENCRYPTION_TEST_SCOPE = InboxScope("encrypted-account", "encrypted-client")
private val ENCRYPTION_TEST_LIMITS = NotificationInboxLimits(
    maxIdentifierUtf8Bytes = 256,
    maxCursorUtf8Bytes = 256,
    maxReasonUtf8Bytes = 256,
    maxRawEnvelopeBytes = 65_536,
    maxDecryptedProtoBytes = 65_536,
    maxBatchBlobBytes = 262_144,
    maxRowsPerRead = 16,
    maxChildrenPerEvent = 8,
    maxRetryCount = 3
)
private const val CORRECT_TEST_KEY = "correct-notification-inbox-key-00000000000000000000000000000000"
private const val WRONG_TEST_KEY = "wrong-notification-inbox-key-000000000000000000000000000000000"
private const val SQLITE_HEADER = "SQLite format 3"
private val PROVEN_REAL_GATES = setOf(
    NotificationExtensionProductionGate.VALIDATED_APP_GROUP_ROOT,
    NotificationExtensionProductionGate.ENCRYPTED_HANDOFF_STORAGE,
    NotificationExtensionProductionGate.SHARED_KEYCHAIN_AUTHENTICATION,
    NotificationExtensionProductionGate.RAW_EVENT_TRANSPORT_CAPTURE,
    NotificationExtensionProductionGate.RECEIVE_ONLY_CRYPTO_ASSEMBLY,
    NotificationExtensionProductionGate.NOTIFICATION_AVS_SWIFT_BRIDGE,
    NotificationExtensionProductionGate.BOUNDED_STORAGE_ENFORCEMENT
)
private val EXTERNALLY_VERIFIABLE_REAL_GATES_FOR_TEST = setOf(
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
private val EXTERNALLY_VERIFIABLE_REAL_GATE_MASK_FOR_TEST =
    EXTERNALLY_VERIFIABLE_REAL_GATES_FOR_TEST.fold(0L) { mask, gate -> mask or gate.bitMask }
private val CODE_OWNED_UNPROVEN_REAL_GATES =
    NotificationExtensionProductionGate.entries.toSet() -
            PROVEN_REAL_GATES -
            EXTERNALLY_VERIFIABLE_REAL_GATES_FOR_TEST
