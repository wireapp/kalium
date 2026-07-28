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

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ForegroundNotificationInboxImporterTest {
    @Test
    fun givenMainCommitSucceededAndHandoffMarkFailed_whenRetried_thenCommitIsReplayedBeforeMark() = runBlocking {
        val handoff = FakeForegroundImportHandoff(
            marks = ArrayDeque(
                listOf(
                    ForegroundImportMarkResult.StorageFailure(NotificationInboxFailure.STORAGE_UNAVAILABLE),
                    ForegroundImportMarkResult.Marked(markedRawParentCount = 1, markedChildCount = 0)
                )
            )
        )
        val commitResults = ArrayDeque<ForegroundImportCommitResult>(
            listOf(
                ForegroundImportCommitResult.Committed(null),
                ForegroundImportCommitResult.AlreadyCommitted(null)
            )
        )
        var commitCount = 0
        val importer = ForegroundNotificationInboxImporter(
            handoff = handoff,
            committer = ForegroundNotificationInboxCommitter {
                commitCount += 1
                commitResults.removeFirst()
            },
            nowEpochMillis = { IMPORTED_AT_EPOCH_MILLIS }
        )

        assertIs<ForegroundImportRunResult.RetryableFailure>(importer.importNext(TEST_SCOPE))
        assertEquals(0, handoff.markedCount)
        assertIs<ForegroundImportRunResult.Imported>(importer.importNext(TEST_SCOPE))
        assertEquals(2, commitCount)
        assertEquals(1, handoff.markedCount)
    }

    @Test
    fun givenMainCommitFailed_whenImporting_thenHandoffRemainsPending() = runBlocking {
        val handoff = FakeForegroundImportHandoff()
        val importer = ForegroundNotificationInboxImporter(
            handoff = handoff,
            committer = ForegroundNotificationInboxCommitter {
                ForegroundImportCommitResult.RetryableFailure
            },
            nowEpochMillis = { IMPORTED_AT_EPOCH_MILLIS }
        )

        assertIs<ForegroundImportRunResult.RetryableFailure>(importer.importNext(TEST_SCOPE))
        assertEquals(0, handoff.markAttempts)
        assertEquals(0, handoff.markedCount)
    }

    @Test
    fun givenChildOnlySnapshotAndRawDisposition_whenImporting_thenIntegrityConflictIsReturned() = runBlocking {
        val handoff = FakeForegroundImportHandoff()
        val importer = ForegroundNotificationInboxImporter(
            handoff = handoff,
            committer = ForegroundNotificationInboxCommitter {
                ForegroundImportCommitResult.Committed(
                    ForegroundRawImportDisposition.DURABLY_QUEUED_FOR_FOREGROUND
                )
            },
            nowEpochMillis = { IMPORTED_AT_EPOCH_MILLIS }
        )

        assertIs<ForegroundImportRunResult.IntegrityConflict>(importer.importNext(TEST_SCOPE))
        assertEquals(0, handoff.markAttempts)
    }
}

private class FakeForegroundImportHandoff(
    private val snapshot: ForegroundImportSnapshot = childOnlySnapshot(),
    private val marks: ArrayDeque<ForegroundImportMarkResult> = ArrayDeque(
        listOf(ForegroundImportMarkResult.Marked(markedRawParentCount = 1, markedChildCount = 0))
    )
) : ForegroundImportHandoff {
    var markAttempts: Int = 0
    var markedCount: Int = 0

    override suspend fun readNext(scope: InboxScope): InboxReadResult<ForegroundImportSnapshot?> =
        InboxReadResult.Success(snapshot)

    override suspend fun markImported(
        snapshot: ForegroundImportSnapshot,
        rawDisposition: ForegroundRawImportDisposition?,
        importedAtEpochMillis: Long
    ): ForegroundImportMarkResult {
        markAttempts += 1
        val result = marks.removeFirst()
        if (result is ForegroundImportMarkResult.Marked) markedCount += 1
        return result
    }
}

private fun childOnlySnapshot(): ForegroundImportSnapshot = ForegroundImportSnapshot(
    protocolVersion = NOTIFICATION_INBOX_CONTRACT_VERSION,
    snapshotMaxIngestSequence = 1L,
    unit = ForegroundImportUnit(
        scope = TEST_SCOPE,
        parentIngestSequence = 1L,
        parentServerEventId = "event-1",
        rawEnvelopeSha256 = "raw-sha",
        rawEnvelopeFormatVersion = 1,
        serverTimestampEpochMillis = null,
        isTransient = false,
        associatedCursor = "cursor-1",
        deliverySource = RawEnvelopeDeliverySource.FOREGROUND_SYNC,
        receivedAtEpochMillis = 1L,
        receiveState = RawReceiveState.COMPLETED,
        foregroundRecoveryRequired = false,
        recoveryReason = null,
        rawImport = null,
        children = emptyList(),
        parentRecordToken = "parent-token"
    ),
    hasMore = false,
    snapshotToken = "snapshot-token",
    issuingStore = Any()
)

private val TEST_SCOPE = InboxScope("account", "client")
private const val IMPORTED_AT_EPOCH_MILLIS = 42L
