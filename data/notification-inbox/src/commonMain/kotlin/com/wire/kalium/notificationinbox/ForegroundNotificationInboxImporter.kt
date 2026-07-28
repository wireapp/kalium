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

package com.wire.kalium.notificationinbox

/**
 * Foreground main-database half of the notification-inbox handoff.
 *
 * Implementations must commit the complete [ForegroundImportSnapshot] in one main-database
 * transaction. Every application-message upsert and side effect must be guarded by the supplied
 * parent/child record tokens. Replaying the same snapshot must return
 * [ForegroundImportCommitResult.AlreadyCommitted] without repeating any side effect.
 *
 * The caller must hold the account process lock for the complete [importNext] call.
 */
public fun interface ForegroundNotificationInboxCommitter {
    public suspend fun commit(snapshot: ForegroundImportSnapshot): ForegroundImportCommitResult
}

/**
 * Result of the main-database transaction.
 *
 * A result for a snapshot containing [ForegroundImportUnit.rawImport] must include the disposition
 * committed by the main database. A child-only snapshot must use `null`.
 */
public sealed interface ForegroundImportCommitResult {
    public data class Committed(
        public val rawDisposition: ForegroundRawImportDisposition?
    ) : ForegroundImportCommitResult

    public data class AlreadyCommitted(
        public val rawDisposition: ForegroundRawImportDisposition?
    ) : ForegroundImportCommitResult

    public data object RetryableFailure : ForegroundImportCommitResult
    public data object TerminalFailure : ForegroundImportCommitResult
}

/** One bounded foreground import attempt. */
public sealed interface ForegroundImportRunResult {
    public data object Drained : ForegroundImportRunResult
    public data class Imported(public val hasMore: Boolean) : ForegroundImportRunResult
    public data object RetryableFailure : ForegroundImportRunResult
    public data object TerminalFailure : ForegroundImportRunResult
    public data object IntegrityConflict : ForegroundImportRunResult
}

/**
 * Crash-replay importer for exactly one complete parent.
 *
 * Main-database commit always happens before the handoff row is marked imported. A crash between
 * those commits replays the same opaque tokens; the committer must report `AlreadyCommitted`, after
 * which this importer completes the handoff compare-and-set. The ordering prevents both message
 * loss and duplicate foreground side effects without pretending two independent databases share a
 * transaction.
 */
public class ForegroundNotificationInboxImporter internal constructor(
    private val handoff: ForegroundImportHandoff,
    private val committer: ForegroundNotificationInboxCommitter,
    private val nowEpochMillis: () -> Long
) {
    public constructor(
        store: NotificationInboxStore,
        committer: ForegroundNotificationInboxCommitter,
        nowEpochMillis: () -> Long
    ) : this(
        handoff = NotificationInboxStoreHandoff(store),
        committer = committer,
        nowEpochMillis = nowEpochMillis
    )

    public suspend fun importNext(scope: InboxScope): ForegroundImportRunResult {
        val snapshot = when (val read = handoff.readNext(scope)) {
            is InboxReadResult.Success -> read.value ?: return ForegroundImportRunResult.Drained
            is InboxReadResult.StorageFailure -> return read.reason.toImportFailure()
        }
        val commit = committer.commit(snapshot)
        val disposition = when (commit) {
            is ForegroundImportCommitResult.Committed -> commit.rawDisposition
            is ForegroundImportCommitResult.AlreadyCommitted -> commit.rawDisposition
            ForegroundImportCommitResult.RetryableFailure -> return ForegroundImportRunResult.RetryableFailure
            ForegroundImportCommitResult.TerminalFailure -> return ForegroundImportRunResult.TerminalFailure
        }
        if (!snapshot.unit.accepts(disposition)) return ForegroundImportRunResult.IntegrityConflict
        return when (
            val marked = handoff.markImported(
                snapshot = snapshot,
                rawDisposition = disposition,
                importedAtEpochMillis = nowEpochMillis()
            )
        ) {
            is ForegroundImportMarkResult.Marked,
            ForegroundImportMarkResult.AlreadyImported -> ForegroundImportRunResult.Imported(snapshot.hasMore)
            ForegroundImportMarkResult.IntegrityConflict -> ForegroundImportRunResult.IntegrityConflict
            is ForegroundImportMarkResult.StorageFailure -> marked.reason.toImportFailure()
        }
    }
}

internal interface ForegroundImportHandoff {
    suspend fun readNext(scope: InboxScope): InboxReadResult<ForegroundImportSnapshot?>

    suspend fun markImported(
        snapshot: ForegroundImportSnapshot,
        rawDisposition: ForegroundRawImportDisposition?,
        importedAtEpochMillis: Long
    ): ForegroundImportMarkResult
}

private class NotificationInboxStoreHandoff(
    private val store: NotificationInboxStore
) : ForegroundImportHandoff {
    override suspend fun readNext(scope: InboxScope): InboxReadResult<ForegroundImportSnapshot?> =
        store.readNextForegroundImportSnapshot(scope)

    override suspend fun markImported(
        snapshot: ForegroundImportSnapshot,
        rawDisposition: ForegroundRawImportDisposition?,
        importedAtEpochMillis: Long
    ): ForegroundImportMarkResult =
        store.markForegroundImportSnapshotImported(snapshot, rawDisposition, importedAtEpochMillis)
}

private fun ForegroundImportUnit.accepts(disposition: ForegroundRawImportDisposition?): Boolean =
    (rawImport == null && disposition == null) || (rawImport != null && disposition != null)

private fun NotificationInboxFailure.toImportFailure(): ForegroundImportRunResult = when (this) {
    NotificationInboxFailure.STORAGE_UNAVAILABLE,
    NotificationInboxFailure.CLOSED,
    NotificationInboxFailure.UNEXPECTED_PLATFORM_FAILURE -> ForegroundImportRunResult.RetryableFailure

    NotificationInboxFailure.INVALID_INPUT,
    NotificationInboxFailure.CONFIGURED_LIMIT_EXCEEDED,
    NotificationInboxFailure.INCOMPATIBLE_SCHEMA,
    NotificationInboxFailure.CORRUPT_STATE,
    NotificationInboxFailure.ACCOUNT_NOT_ACTIVE,
    NotificationInboxFailure.ACCOUNT_TOMBSTONED,
    NotificationInboxFailure.CURSOR_CUTOVER_REQUIRED,
    NotificationInboxFailure.CURSOR_RECOVERY_REQUIRED -> ForegroundImportRunResult.TerminalFailure
}
