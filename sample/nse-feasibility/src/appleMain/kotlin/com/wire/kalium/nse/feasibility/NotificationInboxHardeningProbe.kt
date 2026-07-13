/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

package com.wire.kalium.nse.feasibility

import com.wire.kalium.notificationinbox.DecryptionState
import com.wire.kalium.notificationinbox.ForegroundImportMarkResult
import com.wire.kalium.notificationinbox.ForegroundImportState
import com.wire.kalium.notificationinbox.InboxMutationResult
import com.wire.kalium.notificationinbox.InboxReadResult
import com.wire.kalium.notificationinbox.InboxScope
import com.wire.kalium.notificationinbox.NotificationInboxAccountRemoval
import com.wire.kalium.notificationinbox.NotificationInboxCleanupRequest
import com.wire.kalium.notificationinbox.NotificationInboxCleanupResult
import com.wire.kalium.notificationinbox.NotificationInboxFailure
import com.wire.kalium.notificationinbox.NotificationInboxLimits
import com.wire.kalium.notificationinbox.NotificationInboxRemovalResult
import com.wire.kalium.notificationinbox.NotificationInboxStorageAdmission
import com.wire.kalium.notificationinbox.NotificationInboxStorageFootprint
import com.wire.kalium.notificationinbox.NotificationInboxStore
import com.wire.kalium.notificationinbox.NotificationState
import com.wire.kalium.notificationinbox.RawEnvelopeDeliverySource
import com.wire.kalium.notificationinbox.RawEventStageResult
import com.wire.kalium.notificationinbox.RawEventWrite
import com.wire.kalium.notificationinbox.ReceiveChildWrite
import com.wire.kalium.notificationinbox.ReceiveChildrenStageResult
import com.wire.kalium.notificationinbox.ReceiveChildrenWrite
import com.wire.kalium.notificationinbox.ReceiveClassification
import com.wire.kalium.notificationinbox.ReceiveProtocol
import com.wire.kalium.notificationinbox.SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID
import com.wire.kalium.notificationinbox.SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
import com.wire.kalium.notificationinbox.SYNTHETIC_NOTIFICATION_INBOX_CUTOVER_ID
import com.wire.kalium.notificationinbox.SharedCursorActivationResult
import com.wire.kalium.notificationinbox.SyntheticNotificationInboxOpenResult
import com.wire.kalium.notificationinbox.SyntheticNotificationInboxStorageMeasurer
import com.wire.kalium.notificationinbox.SyntheticPlaintextNotificationInboxFactory
import com.wire.kalium.notificationinbox.evaluateNotificationInboxStorageAdmission
import com.wire.kalium.notificationinbox.protocolMessageUidChildIdempotencyKey
import com.wire.kalium.synccoordination.AppleProcessLockFactory
import com.wire.kalium.synccoordination.ProcessLockAcquireResult

internal suspend fun runNotificationInboxHardeningProbe(sharedRoot: String): String {
    val lock = AppleProcessLockFactory(sharedRoot).tryAcquire(M9_SCOPE.accountId, M9_SCOPE.clientId)
    check(lock is ProcessLockAcquireResult.Acquired)
    try {
        probeStep("phased-cutover") { provePhasedCutover(sharedRoot) }
        val recoveryToken = probeValue("global-recovery") { proveRecoveryStopsCursor(sharedRoot) }
        probeStep("cleanup") { proveCleanupAndRollback(sharedRoot) }
        val tombstoneToken = probeValue("tombstone") { proveTombstoneAndRollback(sharedRoot) }
        probeStep("footprint") { provePhysicalFootprintAdmission(sharedRoot) }
        return "phasedCutover=true; recoveryStopsStage=true; recoveryTokenAck=true; " +
                "cleanupCompleteParent=true; cursorAnchorRetained=true; cleanupRollback=true; " +
                "tombstoneReplay=true; tombstoneConflict=true; postTombstoneStageRefused=true; " +
                "tombstoneRollback=true; sidecarsAccounted=true; admissionOverflowClosed=true; lockHeld=true; " +
                "recoveryToken=$recoveryToken; tombstoneToken=$tombstoneToken"
    } finally {
        lock.lease.release()
    }
}

private suspend inline fun probeStep(name: String, crossinline block: suspend () -> Unit) {
    runCatching { block() }.getOrElse { failure ->
        error("$name: ${failure.message ?: failure}")
    }
}

private suspend inline fun <T> probeValue(name: String, crossinline block: suspend () -> T): T =
    runCatching { block() }.getOrElse { failure ->
        error("$name: ${failure.message ?: failure}")
    }

private suspend fun provePhasedCutover(sharedRoot: String) {
    val store = factory(sharedRoot, "cutover").openWithCursorPreparedOnly().openedStore()
    try {
        check(store.readCursor(M9_SCOPE).storageFailure() == NotificationInboxFailure.CURSOR_CUTOVER_REQUIRED)
        check(
            (store.stageRawEvent(rawEvent("cutover-blocked", "cursor-blocked", 1L)) as? RawEventStageResult.StorageFailure)
                ?.reason == NotificationInboxFailure.CURSOR_CUTOVER_REQUIRED
        )
        check(
            store.activatePreparedSharedCursor(M9_SCOPE, SYNTHETIC_NOTIFICATION_INBOX_CUTOVER_ID) is
                    SharedCursorActivationResult.Activated
        )
        check(store.stageRawEvent(rawEvent("cutover-active", "cursor-active", 2L)) is RawEventStageResult.Inserted)
    } finally {
        store.close()
    }
}

private suspend fun proveRecoveryStopsCursor(sharedRoot: String): String {
    val store = factory(sharedRoot, "recovery").open().openedStore()
    try {
        check(store.stageRawEvent(rawEvent("recovery-a", "cursor-recovery-a", 1L)) is RawEventStageResult.Inserted)
        check(store.requireCursorGlobalRecovery(M9_SCOPE, "SYNTHETIC_RECOVERY", 2L) == InboxMutationResult.Success)
        val blocked = store.stageRawEvent(rawEvent("recovery-b", "cursor-recovery-b", 3L))
        check((blocked as? RawEventStageResult.StorageFailure)?.reason == NotificationInboxFailure.CURSOR_RECOVERY_REQUIRED)
        val signal = store.readPendingGlobalRecovery(M9_SCOPE, 1).successValue().signals.single()
        check(store.acknowledgeGlobalRecovery(M9_SCOPE, signal.signalToken, 4L) == InboxMutationResult.Success)
        check(store.acknowledgeGlobalRecovery(M9_SCOPE, signal.signalToken, 4L) == InboxMutationResult.Success)
        check(store.readPendingGlobalRecovery(M9_SCOPE, 1).successValue().signals.isEmpty())
        val stillBlocked = store.stageRawEvent(rawEvent("recovery-c", "cursor-recovery-c", 5L))
        check(
            (stillBlocked as? RawEventStageResult.StorageFailure)?.reason ==
                    NotificationInboxFailure.CURSOR_RECOVERY_REQUIRED
        )
        return signal.signalToken
    } finally {
        store.close()
    }
}

private suspend fun proveCleanupAndRollback(sharedRoot: String) {
    val directory = directory(sharedRoot, "cleanup")
    val store = SyntheticPlaintextNotificationInboxFactory(directory, M9_LIMITS).open().openedStore()
    try {
        stageImportedParent(store, "cleanup-a", "cursor-cleanup-a", 1L, 100L)
        stageImportedParent(store, "cleanup-anchor", "cursor-cleanup-anchor", 2L, 100L)
        val result = store.cleanupImported(NotificationInboxCleanupRequest(M9_SCOPE, 100L, 1))
        check(result is NotificationInboxCleanupResult.Cleaned) { "first cleanup result=$result" }
        check(result.deletedParentRows == 1 && result.deletedChildRows == 1 && !result.hasMoreEligibleRows) {
            "first cleanup counts=$result"
        }
        check(store.readCursor(M9_SCOPE).successValue()?.sourceServerEventId == "cleanup-anchor") {
            "cursor anchor changed"
        }
        val secondCleanup = store.cleanupImported(NotificationInboxCleanupRequest(M9_SCOPE, 100L, 1))
        check(secondCleanup is NotificationInboxCleanupResult.NothingEligible) { "second cleanup=$secondCleanup" }
    } finally {
        store.close()
    }

    val rollbackDirectory = directory(sharedRoot, "cleanup-rollback")
    val injected = SyntheticPlaintextNotificationInboxFactory(rollbackDirectory, M9_LIMITS)
        .openWithFailureBeforeCleanupParentDelete().openedStore()
    try {
        stageImportedParent(injected, "cleanup-rollback-a", "cursor-cleanup-rollback-a", 1L, 100L)
        check(
            injected.stageRawEvent(rawEvent("cleanup-rollback-anchor", "cursor-cleanup-rollback-anchor", 2L)) is
                    RawEventStageResult.Inserted
        )
        val injectedResult = injected.cleanupImported(NotificationInboxCleanupRequest(M9_SCOPE, 100L, 1))
        check(injectedResult is NotificationInboxCleanupResult.StorageFailure) { "injected cleanup=$injectedResult" }
    } finally {
        injected.close()
    }
    val reopened = SyntheticPlaintextNotificationInboxFactory(rollbackDirectory, M9_LIMITS).open().openedStore()
    try {
        val reopenedResult = reopened.cleanupImported(NotificationInboxCleanupRequest(M9_SCOPE, 100L, 1))
        check(reopenedResult is NotificationInboxCleanupResult.Cleaned) { "rollback cleanup=$reopenedResult" }
    } finally {
        reopened.close()
    }
}

private suspend fun proveTombstoneAndRollback(sharedRoot: String): String {
    val removal = NotificationInboxAccountRemoval(M9_SCOPE, "removal-v1", "ACCOUNT_REMOVED", 10L)
    val rollbackDirectory = directory(sharedRoot, "tombstone-rollback")
    val injected = SyntheticPlaintextNotificationInboxFactory(rollbackDirectory, M9_LIMITS)
        .openWithFailureBeforeTombstoneCommit().openedStore()
    try {
        check(
            injected.stageRawEvent(rawEvent("tombstone-rollback", "cursor-tombstone-rollback", 1L)) is
                    RawEventStageResult.Inserted
        )
        check(injected.tombstoneAccount(removal) is NotificationInboxRemovalResult.StorageFailure)
    } finally {
        injected.close()
    }
    val rollbackReopened = SyntheticPlaintextNotificationInboxFactory(rollbackDirectory, M9_LIMITS).open().openedStore()
    try {
        check(
            rollbackReopened.stageRawEvent(rawEvent("tombstone-rollback", "cursor-tombstone-rollback", 1L)) is
                    RawEventStageResult.ExactDuplicate
        )
    } finally {
        rollbackReopened.close()
    }

    val store = factory(sharedRoot, "tombstone").open().openedStore()
    try {
        check(store.stageRawEvent(rawEvent("tombstone-a", "cursor-tombstone-a", 1L)) is RawEventStageResult.Inserted)
        val first = store.tombstoneAccount(removal)
        check(first is NotificationInboxRemovalResult.Tombstoned)
        check(store.tombstoneAccount(removal) is NotificationInboxRemovalResult.AlreadyTombstoned)
        check(
            store.tombstoneAccount(removal.copy(removalId = "other-removal")) ==
                    NotificationInboxRemovalResult.IntegrityConflict
        )
        val blocked = store.stageRawEvent(rawEvent("tombstone-b", "cursor-tombstone-b", 2L))
        check((blocked as? RawEventStageResult.StorageFailure)?.reason == NotificationInboxFailure.ACCOUNT_TOMBSTONED)
        return first.tombstoneToken
    } finally {
        store.close()
    }
}

private fun provePhysicalFootprintAdmission(sharedRoot: String) {
    val footprint = SyntheticNotificationInboxStorageMeasurer(directory(sharedRoot, "cleanup"))
        .measure().successValue()
    val total = checkNotNull(footprint.totalBytesOrNull())
    check(total > 0L)
    check(
        evaluateNotificationInboxStorageAdmission(footprint, total + 1_024L, 512L) is
                NotificationInboxStorageAdmission.Admitted
    )
    check(
        evaluateNotificationInboxStorageAdmission(footprint, total, 1L) is
                NotificationInboxStorageAdmission.CleanupRequired
    )
    check(
        evaluateNotificationInboxStorageAdmission(
            NotificationInboxStorageFootprint(Long.MAX_VALUE, 1L, 0L, 0L),
            Long.MAX_VALUE,
            1L
        ) == NotificationInboxStorageAdmission.InvalidMeasurement
    )
}

private suspend fun stageImportedParent(
    store: NotificationInboxStore,
    eventId: String,
    cursor: String,
    receivedAt: Long,
    importedAt: Long
) {
    check(store.stageRawEvent(rawEvent(eventId, cursor, receivedAt)) is RawEventStageResult.Inserted)
    val child = ReceiveChildWrite(
        scope = M9_SCOPE,
        parentServerEventId = eventId,
        itemIndex = 0,
        idempotencyKey = protocolMessageUidChildIdempotencyKey("message-$eventId"),
        conversationId = "conversation-$eventId",
        senderId = "sender",
        senderClientId = "sender-client",
        protocol = ReceiveProtocol.MLS,
        messageTimestampEpochMillis = receivedAt,
        decryptedProto = byteArrayOf(1, 2, 3),
        cryptoStateApplied = true,
        receiveClassification = ReceiveClassification.APPLICATION_MESSAGE,
        failureClassification = null,
        decryptionState = DecryptionState.DECRYPTED,
        notificationState = NotificationState.SUPPRESSED,
        importState = ForegroundImportState.PENDING,
        retryCount = 0
    )
    check(
        store.stageReceiveChildren(ReceiveChildrenWrite(M9_SCOPE, eventId, listOf(child))) is
                ReceiveChildrenStageResult.Stored
    )
    val snapshot = checkNotNull(store.readNextForegroundImportSnapshot(M9_SCOPE).successValue())
    check(snapshot.unit.parentServerEventId == eventId)
    check(store.markForegroundImportSnapshotImported(snapshot, null, importedAt) is ForegroundImportMarkResult.Marked)
}

private fun rawEvent(eventId: String, cursor: String, receivedAt: Long): RawEventWrite = RawEventWrite(
    scope = M9_SCOPE,
    serverEventId = eventId,
    rawEnvelope = "{\"type\":\"$eventId\"}".encodeToByteArray(),
    rawEnvelopeFormatVersion = 1,
    serverTimestampEpochMillis = receivedAt,
    isTransient = false,
    associatedCursor = cursor,
    deliverySource = RawEnvelopeDeliverySource.SYNTHETIC_FEASIBILITY,
    receivedAtEpochMillis = receivedAt
)

private fun factory(sharedRoot: String, suffix: String): SyntheticPlaintextNotificationInboxFactory =
    SyntheticPlaintextNotificationInboxFactory(directory(sharedRoot, suffix), M9_LIMITS)

private fun directory(sharedRoot: String, suffix: String): String =
    "$sharedRoot/kalium-nse/v1/synthetic-m9-$suffix/handoff"

private fun SyntheticNotificationInboxOpenResult.openedStore(): NotificationInboxStore = when (this) {
    is SyntheticNotificationInboxOpenResult.Opened -> store
    is SyntheticNotificationInboxOpenResult.Failure -> error("Synthetic M9 inbox open failed: $reason")
}

private fun <T> InboxReadResult<T>.successValue(): T = when (this) {
    is InboxReadResult.Success -> value
    is InboxReadResult.StorageFailure -> error("Synthetic M9 inbox read failed: $reason")
}

private fun <T> InboxReadResult<T>.storageFailure(): NotificationInboxFailure? =
    (this as? InboxReadResult.StorageFailure)?.reason

private val M9_SCOPE = InboxScope(
    SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID,
    SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
)
private val M9_LIMITS = NotificationInboxLimits(
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
