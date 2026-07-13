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

// Scenario timestamps and indexes are fixed probe fixtures, not production policy values.
@file:Suppress("MagicNumber")

package com.wire.kalium.nse.feasibility

import com.wire.kalium.notificationinbox.DecryptionState
import com.wire.kalium.notificationinbox.ForegroundImportState
import com.wire.kalium.notificationinbox.InboxReadResult
import com.wire.kalium.notificationinbox.InboxScope
import com.wire.kalium.notificationinbox.NotificationInboxLimits
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
import com.wire.kalium.notificationinbox.SyntheticNotificationInboxOpenResult
import com.wire.kalium.notificationinbox.SyntheticPlaintextNotificationInboxFactory
import com.wire.kalium.notificationinbox.fallbackChildIdempotencyKey
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import com.wire.kalium.synccoordination.AppleProcessLockFactory
import com.wire.kalium.synccoordination.ProcessLockAcquireResult
import platform.Foundation.NSUUID

internal suspend fun runSyntheticNotificationInboxProbe(sharedRoot: String): String {
    val lock = AppleProcessLockFactory(sharedRoot).tryAcquire(
        SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID,
        SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
    )
    check(lock is ProcessLockAcquireResult.Acquired) { "Synthetic inbox lock acquisition=$lock" }
    try {
        val directory = probeDirectory(sharedRoot, "main")
        val factory = SyntheticPlaintextNotificationInboxFactory(directory, PROBE_LIMITS)
        val firstStore = factory.openStore()
        val firstPass = try {
            runFirstPass(firstStore)
        } finally {
            firstStore.close()
            firstStore.close()
        }

        val reopened = factory.openStore()
        try {
            val cursor = reopened.readCursor(PROBE_SCOPE).successValue()
            check(cursor?.value == CURSOR_C)
            val children = reopened.readPendingImportChildren(PROBE_SCOPE, PROBE_CHILD_LIMIT).successValue()
            check(children.children.size == EXPECTED_CHILD_COUNT)
            check(children.children[0].decryptedProto.contentEquals(FIRST_PROTO))
            check(children.children[1].decryptedProto.contentEquals(SECOND_PROTO))
            check(children.children.map { it.itemIndex } == listOf(0, 1))
        } finally {
            reopened.close()
        }

        check(runRawCursorRollbackProbe(sharedRoot))
        check(runChildBatchRollbackProbe(sharedRoot))
        return "$firstPass; reopened=true; rawCursorRollback=true; childBatchRollback=true; " +
                "lockHeld=true; plaintextSyntheticOnly=true; productionEncrypted=false"
    } finally {
        lock.lease.release()
    }
}

private suspend fun runRawCursorRollbackProbe(sharedRoot: String): Boolean {
    val factory = SyntheticPlaintextNotificationInboxFactory(probeDirectory(sharedRoot, "raw-rollback"), PROBE_LIMITS)
    val injected = factory.openWithFailureBeforeCursorUpsert().openStore()
    try {
        val result = injected.stageRawEvent(rawEvent(EVENT_ROLLBACK, RAW_EVENT_ROLLBACK, false, CURSOR_ROLLBACK, 20L))
        check(result is RawEventStageResult.StorageFailure)
        check(injected.readCursor(PROBE_SCOPE).successValue() == null)
        check(injected.readPendingReceive(PROBE_SCOPE, PENDING_PAGE_LIMIT).successValue().events.isEmpty())
    } finally {
        injected.close()
    }
    val reopened = factory.openStore()
    return try {
        reopened.readCursor(PROBE_SCOPE).successValue() == null &&
                reopened.readPendingReceive(PROBE_SCOPE, PENDING_PAGE_LIMIT).successValue().events.isEmpty()
    } finally {
        reopened.close()
    }
}

private suspend fun runChildBatchRollbackProbe(sharedRoot: String): Boolean {
    val factory = SyntheticPlaintextNotificationInboxFactory(probeDirectory(sharedRoot, "child-rollback"), PROBE_LIMITS)
    val initial = factory.openStore()
    try {
        check(initial.stageRawEvent(rawEvent(EVENT_A, RAW_EVENT_A, false, CURSOR_A, 30L)) is RawEventStageResult.Inserted)
    } finally {
        initial.close()
    }
    val injected = factory.openWithFailureBeforeParentReceiveComplete().openStore()
    try {
        check(injected.stageReceiveChildren(childBatch(FIRST_PROTO, SECOND_PROTO)) is ReceiveChildrenStageResult.StorageFailure)
        check(injected.readPendingImportChildren(PROBE_SCOPE, PROBE_CHILD_LIMIT).successValue().children.isEmpty())
        check(injected.readPendingReceive(PROBE_SCOPE, PENDING_PAGE_LIMIT).successValue().events.single().serverEventId == EVENT_A)
    } finally {
        injected.close()
    }
    val reopened = factory.openStore()
    return try {
        reopened.readPendingImportChildren(PROBE_SCOPE, PROBE_CHILD_LIMIT).successValue().children.isEmpty() &&
                reopened.readPendingReceive(PROBE_SCOPE, PENDING_PAGE_LIMIT).successValue().events.single().serverEventId == EVENT_A
    } finally {
        reopened.close()
    }
}

private fun probeDirectory(sharedRoot: String, label: String): String =
    "$sharedRoot/kalium-nse/v1/synthetic-m6-$label-${NSUUID.UUID().UUIDString}/handoff"

private suspend fun runFirstPass(store: NotificationInboxStore): String {
    val callerOwned = RAW_EVENT_A.copyOf()
    val eventA = rawEvent(EVENT_A, callerOwned, transient = false, cursor = CURSOR_A, receivedAt = 1L)
    callerOwned[callerOwned.lastIndex] = MUTATED_BYTE
    check(store.stageRawEvent(eventA) is RawEventStageResult.Inserted)
    check(store.readCursor(PROBE_SCOPE).successValue()?.value == CURSOR_A)

    val duplicateA = rawEvent(EVENT_A, RAW_EVENT_A, transient = false, cursor = CURSOR_A, receivedAt = 2L)
    check(store.stageRawEvent(duplicateA) is RawEventStageResult.ExactDuplicate)
    val conflictA = rawEvent(EVENT_A, RAW_EVENT_CONFLICT, transient = false, cursor = CURSOR_A, receivedAt = 3L)
    check(store.stageRawEvent(conflictA) == RawEventStageResult.IntegrityConflict)
    check(store.readCursor(PROBE_SCOPE).successValue()?.value == CURSOR_A)

    check(store.stageRawEvent(rawEvent(EVENT_B, RAW_EVENT_B, true, null, 4L)) is RawEventStageResult.Inserted)
    check(store.readCursor(PROBE_SCOPE).successValue()?.value == CURSOR_A)
    check(store.stageRawEvent(rawEvent(EVENT_C, RAW_EVENT_C, false, CURSOR_C, 5L)) is RawEventStageResult.Inserted)
    check(store.stageRawEvent(duplicateA) is RawEventStageResult.ExactDuplicate)
    check(store.readCursor(PROBE_SCOPE).successValue()?.value == CURSOR_C)

    val firstPage = store.readPendingReceive(PROBE_SCOPE, PENDING_PAGE_LIMIT).successValue()
    val repeatedPage = store.readPendingReceive(PROBE_SCOPE, PENDING_PAGE_LIMIT).successValue()
    check(firstPage.hasMore && repeatedPage.hasMore)
    check(firstPage.events.map { it.serverEventId } == listOf(EVENT_A, EVENT_B))
    check(repeatedPage.events.map { it.serverEventId } == listOf(EVENT_A, EVENT_B))
    check(firstPage.events.first().rawEnvelope.contentEquals(RAW_EVENT_A))

    val invalidNoCursor = rawEvent(EVENT_INVALID, RAW_EVENT_INVALID, false, null, 6L)
    check(store.stageRawEvent(invalidNoCursor) is RawEventStageResult.StorageFailure)
    check(store.readCursor(PROBE_SCOPE).successValue()?.value == CURSOR_C)

    val batch = childBatch(FIRST_PROTO, SECOND_PROTO)
    val stored = store.stageReceiveChildren(batch)
    check(stored == ReceiveChildrenStageResult.Stored(EXPECTED_CHILD_COUNT, 0))
    check(store.stageReceiveChildren(batch) == ReceiveChildrenStageResult.Stored(0, EXPECTED_CHILD_COUNT))
    check(
        store.stageReceiveChildren(childBatch(FIRST_PROTO_CONFLICT, SECOND_PROTO)) ==
                ReceiveChildrenStageResult.IntegrityConflict
    )

    val pendingChildren = store.readPendingImportChildren(PROBE_SCOPE, SINGLE_CHILD_LIMIT).successValue()
    check(pendingChildren.children.single().decryptedProto.contentEquals(FIRST_PROTO))
    check(pendingChildren.hasMore)
    val rawImports = store.readPendingRawImport(PROBE_SCOPE, PROBE_RAW_IMPORT_LIMIT).successValue()
    check(rawImports.events.map { it.serverEventId } == listOf(EVENT_B, EVENT_C))
    check(
        rawImports.events.all {
            it.rawEnvelope.contentEquals(if (it.serverEventId == EVENT_B) RAW_EVENT_B else RAW_EVENT_C)
        }
    )

    return "rawCursorAtomic=true; duplicate=true; conflict=true; transientNoCursor=true; " +
            "olderReplayNoRegression=true; stableBoundedOrder=true; childBatchAtomic=true; exactBlobs=true"
}

private fun rawEvent(
    eventId: String,
    raw: ByteArray,
    transient: Boolean,
    cursor: String?,
    receivedAt: Long
): RawEventWrite = RawEventWrite(
    scope = PROBE_SCOPE,
    serverEventId = eventId,
    rawEnvelope = raw,
    rawEnvelopeFormatVersion = 1,
    serverTimestampEpochMillis = receivedAt,
    isTransient = transient,
    associatedCursor = cursor,
    deliverySource = RawEnvelopeDeliverySource.SYNTHETIC_FEASIBILITY,
    receivedAtEpochMillis = receivedAt
)

private fun childBatch(firstProto: ByteArray, secondProto: ByteArray): ReceiveChildrenWrite = ReceiveChildrenWrite(
    scope = PROBE_SCOPE,
    parentServerEventId = EVENT_A,
    children = listOf(
        child(itemIndex = 0, proto = firstProto),
        child(itemIndex = 1, proto = secondProto)
    )
)

private fun child(itemIndex: Int, proto: ByteArray): ReceiveChildWrite = ReceiveChildWrite(
    scope = PROBE_SCOPE,
    parentServerEventId = EVENT_A,
    itemIndex = itemIndex,
    idempotencyKey = fallbackChildIdempotencyKey(EVENT_A, itemIndex),
    conversationId = "synthetic-conversation",
    senderId = "synthetic-sender",
    senderClientId = "synthetic-sender-client",
    protocol = ReceiveProtocol.MLS,
    messageTimestampEpochMillis = 10L + itemIndex,
    decryptedProto = proto,
    cryptoStateApplied = true,
    receiveClassification = ReceiveClassification.APPLICATION_MESSAGE,
    failureClassification = null,
    decryptionState = DecryptionState.DECRYPTED,
    notificationState = NotificationState.PENDING,
    importState = ForegroundImportState.PENDING,
    retryCount = 0
)

private fun SyntheticNotificationInboxOpenResult.openStore(): NotificationInboxStore = when (this) {
    is SyntheticNotificationInboxOpenResult.Opened -> store
    is SyntheticNotificationInboxOpenResult.Failure -> error("Synthetic inbox open failed: $reason")
}

private suspend fun SyntheticPlaintextNotificationInboxFactory.openStore(): NotificationInboxStore =
    when (val result = open()) {
        is SyntheticNotificationInboxOpenResult.Opened -> result.store
        is SyntheticNotificationInboxOpenResult.Failure -> error("Synthetic inbox open failed: ${result.reason}")
    }

private fun <T> InboxReadResult<T>.successValue(): T = when (this) {
    is InboxReadResult.Success -> value
    is InboxReadResult.StorageFailure -> error("Synthetic inbox read failed: $reason")
}

private val PROBE_SCOPE = InboxScope(
    accountId = SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID,
    clientId = SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
)
private val PROBE_LIMITS = NotificationInboxLimits(
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
private val RAW_EVENT_A = "{\"type\":\"synthetic-a\",\"unknown\":{\"future\":true}}".encodeToByteArray()
private val RAW_EVENT_B = "{\"type\":\"synthetic-b\",\"transient\":true}".encodeToByteArray()
private val RAW_EVENT_C = "{\"type\":\"synthetic-c\",\"nested\":[1,2,3]}".encodeToByteArray()
private val RAW_EVENT_CONFLICT = "{\"type\":\"synthetic-a-conflict\"}".encodeToByteArray()
private val RAW_EVENT_INVALID = "{\"type\":\"synthetic-invalid\"}".encodeToByteArray()
private val RAW_EVENT_ROLLBACK = "{\"type\":\"synthetic-rollback\"}".encodeToByteArray()
private val FIRST_PROTO = GenericMessage(
    messageId = "synthetic-message-1",
    content = GenericMessage.Content.Text(Text(content = "synthetic one"))
).encodeToByteArray()
private val SECOND_PROTO = GenericMessage(
    messageId = "synthetic-message-2",
    content = GenericMessage.Content.Text(Text(content = "synthetic two"))
).encodeToByteArray()
private val FIRST_PROTO_CONFLICT = GenericMessage(
    messageId = "synthetic-message-1",
    content = GenericMessage.Content.Text(Text(content = "synthetic conflict"))
).encodeToByteArray()

private const val EVENT_A = "synthetic-event-a"
private const val EVENT_B = "synthetic-event-b"
private const val EVENT_C = "synthetic-event-c"
private const val EVENT_INVALID = "synthetic-event-invalid"
private const val EVENT_ROLLBACK = "synthetic-event-rollback"
private const val CURSOR_A = "synthetic-cursor-a"
private const val CURSOR_C = "synthetic-cursor-c"
private const val CURSOR_ROLLBACK = "synthetic-cursor-rollback"
private const val PENDING_PAGE_LIMIT = 2
private const val PROBE_CHILD_LIMIT = 2
private const val SINGLE_CHILD_LIMIT = 1
private const val PROBE_RAW_IMPORT_LIMIT = 4
private const val EXPECTED_CHILD_COUNT = 2
private const val MUTATED_BYTE: Byte = 0
