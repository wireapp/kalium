/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.notificationinbox

/** Stable identity for one coordinated switch from the legacy cursor to the shared inbox cursor. */
public class SharedCursorPreparation(
    public val scope: InboxScope,
    public val cutoverId: String,
    public val legacyCursor: String?,
    public val activatedAtEpochMillis: Long
)

public sealed interface SharedCursorPreparationResult {
    public data object Prepared : SharedCursorPreparationResult
    public data object ExactReplay : SharedCursorPreparationResult
    public data object IntegrityConflict : SharedCursorPreparationResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : SharedCursorPreparationResult
}

public sealed interface SharedCursorActivationResult {
    public data object Activated : SharedCursorActivationResult
    public data object ExactReplay : SharedCursorActivationResult
    public data object IntegrityConflict : SharedCursorActivationResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : SharedCursorActivationResult
}

public class GlobalRecoverySignal internal constructor(
    public val reason: String,
    public val recordedAtEpochMillis: Long,
    /** Lowercase SHA-256 over the versioned scope/reason/time frame. */
    public val signalToken: String
)

public class GlobalRecoveryBatch internal constructor(
    signals: List<GlobalRecoverySignal>,
    public val hasMore: Boolean
) {
    public val signals: List<GlobalRecoverySignal> = signals.toList()
}

public data class NotificationInboxCleanupRequest(
    public val scope: InboxScope,
    public val importedBeforeEpochMillis: Long,
    public val maxParentRows: Int
)

public sealed interface NotificationInboxCleanupResult {
    public data class Cleaned(
        public val deletedParentRows: Int,
        public val deletedChildRows: Int,
        public val deletedRawEnvelopeBytes: Long,
        public val deletedDecryptedProtoBytes: Long,
        public val hasMoreEligibleRows: Boolean
    ) : NotificationInboxCleanupResult

    public data object NothingEligible : NotificationInboxCleanupResult
    public data object IntegrityConflict : NotificationInboxCleanupResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : NotificationInboxCleanupResult
}

public data class NotificationInboxAccountRemoval(
    public val scope: InboxScope,
    /** Stable app-owned identifier persisted before the cross-database removal sequence begins. */
    public val removalId: String,
    public val reason: String,
    public val tombstonedAtEpochMillis: Long
)

public sealed interface NotificationInboxRemovalResult {
    public data class Tombstoned(
        public val tombstoneToken: String,
        public val deletedParentRows: Long,
        public val deletedChildRows: Long,
        public val deletedRecoveryRows: Long
    ) : NotificationInboxRemovalResult

    public data class AlreadyTombstoned(public val tombstoneToken: String) : NotificationInboxRemovalResult
    public data object IntegrityConflict : NotificationInboxRemovalResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : NotificationInboxRemovalResult
}

/**
 * Physical storage measurement made by the platform construction boundary while holding the lock.
 * SQLite sidecars are first-class: omitting WAL, SHM, or rollback-journal bytes is invalid.
 */
public data class NotificationInboxStorageFootprint(
    public val databaseBytes: Long,
    public val walBytes: Long,
    public val sharedMemoryBytes: Long,
    public val rollbackJournalBytes: Long
) {
    @Suppress("ReturnCount")
    public fun totalBytesOrNull(): Long? {
        val values = listOf(databaseBytes, walBytes, sharedMemoryBytes, rollbackJournalBytes)
        if (values.any { it < 0 }) return null
        var total = 0L
        for (value in values) {
            if (total > Long.MAX_VALUE - value) return null
            total += value
        }
        return total
    }
}

public sealed interface NotificationInboxStorageAdmission {
    public data class Admitted(public val projectedBytes: Long) : NotificationInboxStorageAdmission
    public data class CleanupRequired(public val currentBytes: Long) : NotificationInboxStorageAdmission
    public data object InvalidMeasurement : NotificationInboxStorageAdmission
}

/**
 * Conservative admission check. [reservedWriteBytes] includes the incoming payload, SQLite page
 * growth, encryption overhead, and a platform-approved transaction reserve.
 */
@Suppress("ReturnCount")
public fun evaluateNotificationInboxStorageAdmission(
    footprint: NotificationInboxStorageFootprint,
    hardLimitBytes: Long,
    reservedWriteBytes: Long
): NotificationInboxStorageAdmission {
    val current = footprint.totalBytesOrNull()
        ?: return NotificationInboxStorageAdmission.InvalidMeasurement
    if (hardLimitBytes <= 0 || reservedWriteBytes < 0 || current > Long.MAX_VALUE - reservedWriteBytes) {
        return NotificationInboxStorageAdmission.InvalidMeasurement
    }
    val projected = current + reservedWriteBytes
    return if (projected <= hardLimitBytes) {
        NotificationInboxStorageAdmission.Admitted(projected)
    } else {
        NotificationInboxStorageAdmission.CleanupRequired(current)
    }
}

public const val NOTIFICATION_INBOX_CURSOR_AUTHORITY_VERSION: Int = 1
public const val NOTIFICATION_INBOX_TOMBSTONE_VERSION: Int = 1
public const val SYNTHETIC_NOTIFICATION_INBOX_CUTOVER_ID: String = "synthetic-shared-cursor-cutover-v1"
