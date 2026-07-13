/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("MagicNumber")

package com.wire.kalium.nse.feasibility

import com.wire.kalium.notificationinbox.DecryptionState
import com.wire.kalium.notificationinbox.ForegroundImportMarkResult
import com.wire.kalium.notificationinbox.ForegroundImportState
import com.wire.kalium.notificationinbox.ForegroundRawImportDisposition
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
import com.wire.kalium.notificationinbox.protocolMessageUidChildIdempotencyKey
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import com.wire.kalium.synccoordination.AppleProcessLockFactory
import com.wire.kalium.synccoordination.ProcessLockAcquireResult

internal suspend fun runSyntheticForegroundImportContractProbe(sharedRoot: String): String {
    val lock = AppleProcessLockFactory(sharedRoot).tryAcquire(M8_SCOPE.accountId, M8_SCOPE.clientId)
    check(lock is ProcessLockAcquireResult.Acquired)
    try {
        val store = SyntheticPlaintextNotificationInboxFactory(m8HandoffDirectory(sharedRoot), M8_LIMITS).openStore()
        try {
            stageM8ApplicationParent(store)
            val first = store.readNextForegroundImportSnapshot(M8_SCOPE).successValue()
            checkNotNull(first)
            check(first.unit.children.first().child.decryptedProto.contentEquals(M8_APPLICATION_PROTO))
            check(first.unit.children.map { it.child.itemIndex } == listOf(0, 1))
            val pendingAfterMainFailure = store.readNextForegroundImportSnapshot(M8_SCOPE).successValue()
            check(pendingAfterMainFailure?.snapshotToken == first.snapshotToken)

            stageM8RawParent(store, M8_RAW_EVENT_B, M8_RAW_B, M8_CURSOR_B, 2L)
            val cursorBeforeMark = store.readCursor(M8_SCOPE).successValue()
            check(!first.hasMore)
            check(store.markForegroundImportSnapshotImported(first, null) is ForegroundImportMarkResult.Marked)
            check(store.readCursor(M8_SCOPE).successValue() == cursorBeforeMark)
            check(store.markForegroundImportSnapshotImported(first, null) == ForegroundImportMarkResult.AlreadyImported)

            val second = checkNotNull(store.readNextForegroundImportSnapshot(M8_SCOPE).successValue())
            check(second.unit.parentServerEventId == M8_RAW_EVENT_B)
            check(
                store.markForegroundImportSnapshotImported(
                    second,
                    ForegroundRawImportDisposition.DURABLY_QUEUED_FOR_FOREGROUND
                ) is ForegroundImportMarkResult.Marked
            )
            check(
                store.markForegroundImportSnapshotImported(
                    second,
                    ForegroundRawImportDisposition.DURABLY_QUEUED_FOR_FOREGROUND
                ) == ForegroundImportMarkResult.AlreadyImported
            )
            check(store.readCursor(M8_SCOPE).successValue() == cursorBeforeMark)
            check(store.readPendingReceive(M8_SCOPE, 8).successValue().events.isEmpty())
            check(
                store.stageRawEvent(rawM8Event(M8_APPLICATION_EVENT, M8_CONFLICT_RAW, M8_CURSOR_A, 3L)) ==
                        RawEventStageResult.IntegrityConflict
            )

            return "mainFailurePending=true; exactSnapshotReplay=true; newRowExcluded=true; " +
                    "postCommitMark=true; alreadyImported=true; conflict=true; rawQueuedNoReplay=true; " +
                    "cursorUnchangedByImport=true; parentAtomic=true; lockHeld=true"
        } finally {
            store.close()
        }
    } finally {
        lock.lease.release()
    }
}

/** Creates an actual M6 database consumed by the standalone native Swift reference prototype. */
internal suspend fun prepareSyntheticForegroundImporterFixture(sharedRoot: String): SyntheticForegroundImporterFixture {
    val lock = AppleProcessLockFactory(sharedRoot).tryAcquire(M8_SCOPE.accountId, M8_SCOPE.clientId)
    check(lock is ProcessLockAcquireResult.Acquired)
    try {
        val store = SyntheticPlaintextNotificationInboxFactory(m8HandoffDirectory(sharedRoot), M8_LIMITS).openStore()
        val snapshotToken = try {
            stageM8ApplicationParent(store)
            stageM8HandshakeParent(store)
            stageM8RawParent(store, M8_RAW_EVENT_C, M8_RAW_C, M8_CURSOR_C, 3L)
            checkNotNull(store.readNextForegroundImportSnapshot(M8_SCOPE).successValue()).snapshotToken
        } finally {
            store.close()
        }
        return SyntheticForegroundImporterFixture(
            handoffPath = "${m8HandoffDirectory(sharedRoot)}/synthetic-notification-inbox.sqlite",
            firstSnapshotToken = snapshotToken
        )
    } finally {
        lock.lease.release()
    }
}

internal data class SyntheticForegroundImporterFixture(
    val handoffPath: String,
    val firstSnapshotToken: String
)

private suspend fun stageM8ApplicationParent(store: NotificationInboxStore) {
    check(store.stageRawEvent(rawM8Event(M8_APPLICATION_EVENT, M8_RAW_A, M8_CURSOR_A, 1L)) is RawEventStageResult.Inserted)
    val firstChild = ReceiveChildWrite(
        scope = M8_SCOPE,
        parentServerEventId = M8_APPLICATION_EVENT,
        itemIndex = 0,
        idempotencyKey = protocolMessageUidChildIdempotencyKey(M8_MESSAGE_ID),
        conversationId = M8_CONVERSATION_ID,
        senderId = "synthetic-sender",
        senderClientId = "synthetic-sender-client",
        protocol = ReceiveProtocol.MLS,
        messageTimestampEpochMillis = 10L,
        decryptedProto = M8_APPLICATION_PROTO,
        cryptoStateApplied = true,
        receiveClassification = ReceiveClassification.APPLICATION_MESSAGE,
        failureClassification = null,
        decryptionState = DecryptionState.DECRYPTED,
        notificationState = NotificationState.SUPPRESSED,
        importState = ForegroundImportState.PENDING,
        retryCount = 0
    )
    val secondChild = ReceiveChildWrite(
        scope = M8_SCOPE,
        parentServerEventId = M8_APPLICATION_EVENT,
        itemIndex = 1,
        idempotencyKey = protocolMessageUidChildIdempotencyKey(M8_SECOND_MESSAGE_ID),
        conversationId = M8_CONVERSATION_ID,
        senderId = "synthetic-sender",
        senderClientId = "synthetic-sender-client",
        protocol = ReceiveProtocol.MLS,
        messageTimestampEpochMillis = 10L,
        decryptedProto = M8_SECOND_APPLICATION_PROTO,
        cryptoStateApplied = true,
        receiveClassification = ReceiveClassification.APPLICATION_MESSAGE,
        failureClassification = null,
        decryptionState = DecryptionState.DECRYPTED,
        notificationState = NotificationState.SUPPRESSED,
        importState = ForegroundImportState.PENDING,
        retryCount = 0
    )
    check(
        store.stageReceiveChildren(
            ReceiveChildrenWrite(M8_SCOPE, M8_APPLICATION_EVENT, listOf(firstChild, secondChild))
        ) is
                ReceiveChildrenStageResult.Stored
    )
}

private suspend fun stageM8HandshakeParent(store: NotificationInboxStore) {
    stageM8RawParent(store, M8_HANDSHAKE_EVENT, M8_RAW_B, M8_CURSOR_B, 2L)
    val child = ReceiveChildWrite(
        scope = M8_SCOPE,
        parentServerEventId = M8_HANDSHAKE_EVENT,
        itemIndex = 0,
        idempotencyKey = protocolMessageUidChildIdempotencyKey("synthetic-handshake-1"),
        conversationId = M8_CONVERSATION_ID,
        senderId = "synthetic-sender",
        senderClientId = "synthetic-sender-client",
        protocol = ReceiveProtocol.MLS,
        messageTimestampEpochMillis = 11L,
        decryptedProto = null,
        cryptoStateApplied = true,
        receiveClassification = ReceiveClassification.HANDSHAKE_ONLY,
        failureClassification = null,
        decryptionState = DecryptionState.HANDSHAKE_APPLIED,
        notificationState = NotificationState.NOT_ELIGIBLE,
        importState = ForegroundImportState.PENDING,
        retryCount = 0
    )
    check(
        store.stageReceiveChildren(ReceiveChildrenWrite(M8_SCOPE, M8_HANDSHAKE_EVENT, listOf(child))) is
                ReceiveChildrenStageResult.Stored
    )
}

private suspend fun stageM8RawParent(
    store: NotificationInboxStore,
    eventId: String,
    raw: ByteArray,
    cursor: String,
    receivedAt: Long
) {
    check(store.stageRawEvent(rawM8Event(eventId, raw, cursor, receivedAt)) is RawEventStageResult.Inserted)
}

private fun rawM8Event(eventId: String, raw: ByteArray, cursor: String, receivedAt: Long): RawEventWrite = RawEventWrite(
    scope = M8_SCOPE,
    serverEventId = eventId,
    rawEnvelope = raw,
    rawEnvelopeFormatVersion = 1,
    serverTimestampEpochMillis = receivedAt,
    isTransient = false,
    associatedCursor = cursor,
    deliverySource = RawEnvelopeDeliverySource.SYNTHETIC_FEASIBILITY,
    receivedAtEpochMillis = receivedAt
)

private fun m8HandoffDirectory(sharedRoot: String): String =
    "$sharedRoot/kalium-nse/v1/synthetic-m8-native/handoff"

private suspend fun SyntheticPlaintextNotificationInboxFactory.openStore(): NotificationInboxStore = when (val value = open()) {
    is SyntheticNotificationInboxOpenResult.Opened -> value.store
    is SyntheticNotificationInboxOpenResult.Failure -> error("Synthetic M8 inbox open failed: ${value.reason}")
}

private fun <T> InboxReadResult<T>.successValue(): T = when (this) {
    is InboxReadResult.Success -> value
    is InboxReadResult.StorageFailure -> error("Synthetic M8 inbox read failed: $reason")
}

private val M8_SCOPE = InboxScope(
    SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID,
    SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
)
private val M8_LIMITS = NotificationInboxLimits(
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
private val M8_RAW_A = "{\"type\":\"synthetic-m8-application\"}".encodeToByteArray()
private val M8_RAW_B = "{\"type\":\"synthetic-m8-handshake\"}".encodeToByteArray()
private val M8_RAW_C = "{\"type\":\"synthetic-m8-deferred\"}".encodeToByteArray()
private val M8_CONFLICT_RAW = "{\"type\":\"synthetic-m8-conflict\"}".encodeToByteArray()
private val M8_APPLICATION_PROTO = GenericMessage(
    messageId = "synthetic-message-1",
    content = GenericMessage.Content.Text(Text(content = "synthetic foreground import"))
).encodeToByteArray()
private val M8_SECOND_APPLICATION_PROTO = GenericMessage(
    messageId = "synthetic-message-2",
    content = GenericMessage.Content.Text(Text(content = "synthetic foreground import two"))
).encodeToByteArray()

private const val M8_APPLICATION_EVENT = "synthetic-m8-event-application"
private const val M8_HANDSHAKE_EVENT = "synthetic-m8-event-handshake"
private const val M8_RAW_EVENT_B = "synthetic-m8-event-late"
private const val M8_RAW_EVENT_C = "synthetic-m8-event-raw"
private const val M8_MESSAGE_ID = "synthetic-message-1"
private const val M8_SECOND_MESSAGE_ID = "synthetic-message-2"
private const val M8_CONVERSATION_ID = "synthetic-conversation"
private const val M8_CURSOR_A = "synthetic-m8-cursor-a"
private const val M8_CURSOR_B = "synthetic-m8-cursor-b"
private const val M8_CURSOR_C = "synthetic-m8-cursor-c"
