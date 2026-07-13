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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.notificationinbox

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.notificationinbox.db.NotificationInboxDatabase
import com.wire.kalium.notificationinbox.db.RawEvent
import com.wire.kalium.notificationinbox.db.SelectImportChildBySequence
import com.wire.kalium.notificationinbox.db.SelectReceiveChildByParentItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Suppress("LargeClass", "TooManyFunctions")
internal class SqlDelightNotificationInboxStore(
    private val driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
    private val limits: NotificationInboxLimits,
    private val syntheticOnly: Boolean,
    private val expectedStorageProfile: String,
    private val syntheticFailurePoint: SyntheticNotificationInboxFailurePoint
) : NotificationInboxStore {
    private val database = NotificationInboxDatabase(driver)
    private val queries = database.handoffQueries
    private val closed = AtomicInt(OPEN_STATE)
    private val syntheticFailureArmed = AtomicInt(ARMED_STATE)

    @Suppress("ComplexCondition")
    internal suspend fun validateCompatibility(): NotificationInboxFailure? = withContext(dispatcher) {
        if (!limits.areValid()) return@withContext NotificationInboxFailure.INVALID_INPUT
        try {
            var metadata = queries.selectContractMetadata().awaitAsOneOrNull()
                ?: return@withContext NotificationInboxFailure.CORRUPT_STATE
            if (metadata.blob_storage_format == UNBOUND_STORAGE_PROFILE) {
                queries.bindBlobStorageFormat(expectedStorageProfile)
                metadata = queries.selectContractMetadata().awaitAsOneOrNull()
                    ?: return@withContext NotificationInboxFailure.CORRUPT_STATE
            }
            if (
                metadata.contract_version != NOTIFICATION_INBOX_CONTRACT_VERSION.toLong() ||
                metadata.raw_envelope_format_version != NOTIFICATION_RAW_ENVELOPE_FORMAT_VERSION.toLong() ||
                metadata.child_payload_format_version != NOTIFICATION_CHILD_PAYLOAD_FORMAT_VERSION.toLong() ||
                metadata.blob_storage_format != expectedStorageProfile
            ) {
                NotificationInboxFailure.INCOMPATIBLE_SCHEMA
            } else {
                null
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            NotificationInboxFailure.INCOMPATIBLE_SCHEMA
        }
    }

    @Suppress("ComplexCondition")
    override suspend fun readCursor(scope: InboxScope): InboxReadResult<DurableInboxCursor?> =
        readOperation {
            if (!scope.isValid()) return@readOperation InboxReadResult.StorageFailure(invalidInput())
            if (!scope.isAllowedBy(syntheticOnly)) return@readOperation InboxReadResult.StorageFailure(invalidInput())
            val row = queries.selectCursor(scope.accountId, scope.clientId).awaitAsOneOrNull()
            if (row != null) {
                val source = queries.selectRawEvent(
                    scope.accountId,
                    scope.clientId,
                    row.source_server_event_id
                ).awaitAsOneOrNull()
                if (
                    source == null || source.ingest_sequence != row.source_ingest_sequence ||
                    !source.isStructurallyValid(limits, syntheticOnly) ||
                    !row.cursor_value.isValidStoredValue(limits.maxCursorUtf8Bytes) ||
                    row.updated_at_epoch_millis < 0 ||
                    source.is_transient != 0L || source.associated_cursor != row.cursor_value
                ) {
                    return@readOperation InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
                }
            }
            InboxReadResult.Success(
                row?.let {
                    DurableInboxCursor(
                        value = it.cursor_value,
                        sourceIngestSequence = it.source_ingest_sequence,
                        sourceServerEventId = it.source_server_event_id,
                        updatedAtEpochMillis = it.updated_at_epoch_millis
                    )
                }
            )
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun stageRawEvent(request: RawEventWrite): RawEventStageResult = rawWriteOperation {
        val bytes = request.rawEnvelope
        request.validationFailure(bytes)?.let { return@rawWriteOperation RawEventStageResult.StorageFailure(it) }
        if (
            !request.scope.isAllowedBy(syntheticOnly) ||
            (syntheticOnly && request.deliverySource != RawEnvelopeDeliverySource.SYNTHETIC_FEASIBILITY)
        ) {
            return@rawWriteOperation RawEventStageResult.StorageFailure(invalidInput())
        }

        val digest = sha256LowercaseHex(bytes)
        database.transactionWithResult<RawEventStageResult> {
            val existing = queries.selectRawEvent(
                request.scope.accountId,
                request.scope.clientId,
                request.serverEventId
            ).awaitAsOneOrNull()
            if (existing != null) {
                val cursorInvariantHolds = if (request.isTransient) {
                    true
                } else {
                    val cursor = queries.selectCursor(request.scope.accountId, request.scope.clientId).awaitAsOneOrNull()
                    val cursorSource = cursor?.let {
                        queries.selectRawEvent(
                            request.scope.accountId,
                            request.scope.clientId,
                            it.source_server_event_id
                        ).awaitAsOneOrNull()
                    }
                    cursor != null && cursorSource != null &&
                            cursorSource.isStructurallyValid(limits, syntheticOnly) &&
                            cursorSource.ingest_sequence == cursor.source_ingest_sequence &&
                            cursorSource.is_transient == 0L && cursorSource.associated_cursor == cursor.cursor_value &&
                            cursor.source_ingest_sequence >= existing.ingest_sequence &&
                            (
                                    cursor.source_ingest_sequence != existing.ingest_sequence ||
                                            (
                                                    cursor.cursor_value == request.associatedCursor &&
                                                            cursor.source_server_event_id == request.serverEventId
                                                    )
                                    )
                }
                return@transactionWithResult if (
                    existing.isStructurallyValid(limits, syntheticOnly) &&
                    existing.matches(request, bytes, digest) && cursorInvariantHolds
                ) {
                    RawEventStageResult.ExactDuplicate(existing.ingest_sequence)
                } else {
                    RawEventStageResult.IntegrityConflict
                }
            }
            queries.insertRawEvent(
                account_id = request.scope.accountId,
                client_id = request.scope.clientId,
                server_event_id = request.serverEventId,
                raw_envelope = bytes,
                raw_envelope_sha256 = digest,
                raw_envelope_format_version = request.rawEnvelopeFormatVersion.toLong(),
                server_timestamp_epoch_millis = request.serverTimestampEpochMillis,
                is_transient = request.isTransient.toLong(),
                associated_cursor = request.associatedCursor,
                delivery_source = request.deliverySource.name,
                received_at_epoch_millis = request.receivedAtEpochMillis
            )
            val ingestSequence = queries.selectLastInsertedRowId().awaitAsOne()
            if (!request.isTransient) {
                injectSyntheticFailure(SyntheticNotificationInboxFailurePoint.BEFORE_CURSOR_UPSERT)
                queries.upsertCursor(
                    account_id = request.scope.accountId,
                    client_id = request.scope.clientId,
                    cursor_value = checkNotNull(request.associatedCursor),
                    source_ingest_sequence = ingestSequence,
                    source_server_event_id = request.serverEventId,
                    updated_at_epoch_millis = request.receivedAtEpochMillis
                )
            }
            RawEventStageResult.Inserted(ingestSequence)
        }
    }

    override suspend fun readPendingReceive(
        scope: InboxScope,
        limit: Int
    ): InboxReadResult<PendingRawEventBatch> = readOperation {
        readValidationFailure(scope, limit)?.let { return@readOperation InboxReadResult.StorageFailure(it) }
        database.transactionWithResult<InboxReadResult<PendingRawEventBatch>> {
            val lengths = queries.selectPendingReceiveBlobLengths(
                scope.accountId,
                scope.clientId,
                limit.plusOne()
            ).awaitAsList()
            if (!lengths.map { it.blob_size }.fitBlobBudget(limits.maxRawEnvelopeBytes, limit)) {
                return@transactionWithResult InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            }
            val hasMore = lengths.size > limit
            val rows = lengths.take(limit).map { lengthRow ->
                queries.selectRawEventByIngestSequence(
                    scope.accountId,
                    scope.clientId,
                    lengthRow.ingest_sequence
                ).awaitAsOne()
            }
            InboxReadResult.Success(
                PendingRawEventBatch(rows.map { it.toValidatedPending(limits, syntheticOnly) }, hasMore)
            )
        }
    }

    override suspend fun readPendingRawImport(
        scope: InboxScope,
        limit: Int
    ): InboxReadResult<PendingRawEventBatch> = readOperation {
        readValidationFailure(scope, limit)?.let { return@readOperation InboxReadResult.StorageFailure(it) }
        database.transactionWithResult<InboxReadResult<PendingRawEventBatch>> {
            val lengths = queries.selectPendingRawImportBlobLengths(
                scope.accountId,
                scope.clientId,
                limit.plusOne()
            ).awaitAsList()
            if (!lengths.map { it.blob_size }.fitBlobBudget(limits.maxRawEnvelopeBytes, limit)) {
                return@transactionWithResult InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            }
            val hasMore = lengths.size > limit
            val rows = lengths.take(limit).map { lengthRow ->
                queries.selectRawEventByIngestSequence(
                    scope.accountId,
                    scope.clientId,
                    lengthRow.ingest_sequence
                ).awaitAsOne()
            }
            InboxReadResult.Success(
                PendingRawEventBatch(rows.map { it.toValidatedPending(limits, syntheticOnly) }, hasMore)
            )
        }
    }

    override suspend fun markReceiveCompleted(
        scope: InboxScope,
        serverEventId: String
    ): InboxMutationResult = mutationOperation {
        mutationValidationFailure(scope, serverEventId)?.let {
            return@mutationOperation InboxMutationResult.StorageFailure(it)
        }
        database.transactionWithResult<InboxMutationResult> {
            val existing = queries.selectRawEvent(scope.accountId, scope.clientId, serverEventId).awaitAsOneOrNull()
                ?: return@transactionWithResult InboxMutationResult.Missing
            if (!existing.isStructurallyValid(limits, syntheticOnly)) {
                return@transactionWithResult InboxMutationResult.IntegrityConflict
            }
            when (existing.receive_state) {
                RECEIVE_STATE_COMPLETED -> InboxMutationResult.Success
                RECEIVE_STATE_PENDING -> {
                    queries.markReceiveCompleted(scope.accountId, scope.clientId, serverEventId)
                    if (queries.selectChanges().awaitAsOne() == 1L) {
                        InboxMutationResult.Success
                    } else {
                        InboxMutationResult.IntegrityConflict
                    }
                }
                else -> InboxMutationResult.IntegrityConflict
            }
        }
    }

    override suspend fun deferToForeground(
        scope: InboxScope,
        serverEventId: String,
        reason: String
    ): InboxMutationResult = mutationOperation {
        mutationValidationFailure(scope, serverEventId, reason)?.let {
            return@mutationOperation InboxMutationResult.StorageFailure(it)
        }
        database.transactionWithResult {
            val existing = queries.selectRawEvent(scope.accountId, scope.clientId, serverEventId).awaitAsOneOrNull()
                ?: return@transactionWithResult InboxMutationResult.Missing
            if (!existing.isStructurallyValid(limits, syntheticOnly)) {
                return@transactionWithResult InboxMutationResult.IntegrityConflict
            }
            when {
                existing.receive_state == RECEIVE_STATE_DEFERRED && existing.recovery_reason == reason ->
                    InboxMutationResult.Success
                existing.receive_state != RECEIVE_STATE_PENDING -> InboxMutationResult.IntegrityConflict
                else -> {
                    queries.deferRawEvent(reason, scope.accountId, scope.clientId, serverEventId)
                    if (queries.selectChanges().awaitAsOne() == 1L) {
                        InboxMutationResult.Success
                    } else {
                        InboxMutationResult.IntegrityConflict
                    }
                }
            }
        }
    }

    @Suppress("ComplexCondition")
    override suspend fun recordGlobalRecovery(
        scope: InboxScope,
        reason: String,
        recordedAtEpochMillis: Long
    ): InboxMutationResult = mutationOperation {
        if (
            !scope.isValid() || !scope.isAllowedBy(syntheticOnly) || !reason.isValidReason() ||
            recordedAtEpochMillis < 0
        ) {
            return@mutationOperation InboxMutationResult.StorageFailure(invalidInput())
        }
        queries.insertGlobalRecovery(scope.accountId, scope.clientId, reason, recordedAtEpochMillis)
        InboxMutationResult.Success
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun stageReceiveChildren(request: ReceiveChildrenWrite): ReceiveChildrenStageResult =
        childWriteOperation {
            request.validationFailure()?.let {
                return@childWriteOperation ReceiveChildrenStageResult.StorageFailure(it)
            }
            val prepared = request.children.map { child ->
                val proto = child.decryptedProto
                child.validationFailure(proto)?.let {
                    return@childWriteOperation ReceiveChildrenStageResult.StorageFailure(it)
                }
                PreparedChild(child, proto, proto?.let(::sha256LowercaseHex))
            }
            if (!prepared.map { it.proto?.size?.toLong() }.fitBlobBudget(limits.maxDecryptedProtoBytes, prepared.size)) {
                return@childWriteOperation ReceiveChildrenStageResult.StorageFailure(limitExceeded())
            }

            database.transactionWithResult {
                val parent = queries.selectRawEvent(
                    request.scope.accountId,
                    request.scope.clientId,
                    request.parentServerEventId
                ).awaitAsOneOrNull() ?: return@transactionWithResult ReceiveChildrenStageResult.ParentMissing
                if (!parent.isStructurallyValid(limits, syntheticOnly)) {
                    return@transactionWithResult ReceiveChildrenStageResult.IntegrityConflict
                }

                val existingChildMetadata = queries.selectReceiveChildBlobLengthsForParent(
                    parent.ingest_sequence,
                    limits.maxChildrenPerEvent.toLong() + 1L
                ).awaitAsList()
                if (
                    existingChildMetadata.size > limits.maxChildrenPerEvent ||
                    !existingChildMetadata.map { it.blob_size }
                        .fitBlobBudget(limits.maxDecryptedProtoBytes, existingChildMetadata.size)
                ) {
                    return@transactionWithResult ReceiveChildrenStageResult.IntegrityConflict
                }
                for (candidate in prepared) {
                    val byKey = queries.selectReceiveChildByIdempotencyKey(
                        request.scope.accountId,
                        request.scope.clientId,
                        candidate.value.idempotencyKey
                    ).awaitAsOneOrNull()
                    if (byKey != null && byKey.parent_ingest_sequence != parent.ingest_sequence) {
                        return@transactionWithResult ReceiveChildrenStageResult.IntegrityConflict
                    }
                }
                if (existingChildMetadata.isNotEmpty()) {
                    if (parent.receive_state != RECEIVE_STATE_COMPLETED || existingChildMetadata.size != prepared.size) {
                        return@transactionWithResult ReceiveChildrenStageResult.IntegrityConflict
                    }
                    val exactReplay = prepared.all { candidate ->
                        val metadata = existingChildMetadata.singleOrNull {
                            it.item_index == candidate.value.itemIndex.toLong() &&
                                    it.idempotency_key == candidate.value.idempotencyKey
                        } ?: return@all false
                        queries.selectReceiveChildByParentItem(
                            parent.ingest_sequence,
                            metadata.item_index
                        ).awaitAsOneOrNull()?.matches(candidate.value, candidate.proto, candidate.digest) == true
                    }
                    return@transactionWithResult if (exactReplay) {
                        ReceiveChildrenStageResult.Stored(insertedCount = 0, exactDuplicateCount = prepared.size)
                    } else {
                        ReceiveChildrenStageResult.IntegrityConflict
                    }
                }
                if (parent.receive_state != RECEIVE_STATE_PENDING) {
                    return@transactionWithResult ReceiveChildrenStageResult.IntegrityConflict
                }

                for (candidate in prepared) {
                    val child = candidate.value
                    queries.insertReceiveChild(
                        parent_ingest_sequence = parent.ingest_sequence,
                        account_id = request.scope.accountId,
                        client_id = request.scope.clientId,
                        item_index = child.itemIndex.toLong(),
                        idempotency_key = child.idempotencyKey,
                        conversation_id = child.conversationId,
                        sender_id = child.senderId,
                        sender_client_id = child.senderClientId,
                        protocol = child.protocol.name,
                        message_timestamp_epoch_millis = child.messageTimestampEpochMillis,
                        decrypted_proto = candidate.proto,
                        decrypted_proto_sha256 = candidate.digest,
                        crypto_state_applied = child.cryptoStateApplied.toLong(),
                        receive_classification = child.receiveClassification.name,
                        failure_classification = child.failureClassification,
                        decryption_state = child.decryptionState.name,
                        notification_state = child.notificationState.name,
                        import_state = child.importState.name,
                        retry_count = child.retryCount.toLong()
                    )
                }

                injectSyntheticFailure(SyntheticNotificationInboxFailurePoint.BEFORE_PARENT_RECEIVE_COMPLETE)
                queries.markReceiveCompleted(
                    request.scope.accountId,
                    request.scope.clientId,
                    request.parentServerEventId
                )
                if (queries.selectChanges().awaitAsOne() != 1L) {
                    throw AbortChildBatchForIntegrityConflict()
                }
                ReceiveChildrenStageResult.Stored(insertedCount = prepared.size, exactDuplicateCount = 0)
            }
        }

    override suspend fun readPendingImportChildren(
        scope: InboxScope,
        limit: Int
    ): InboxReadResult<PendingImportChildBatch> = readOperation {
        readValidationFailure(scope, limit)?.let { return@readOperation InboxReadResult.StorageFailure(it) }
        database.transactionWithResult<InboxReadResult<PendingImportChildBatch>> {
            val lengths = queries.selectPendingImportChildBlobLengths(
                scope.accountId,
                scope.clientId,
                limit.plusOne()
            ).awaitAsList()
            if (!lengths.map { it.blob_size }.fitBlobBudget(limits.maxDecryptedProtoBytes, limit)) {
                return@transactionWithResult InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            }
            val hasMore = lengths.size > limit
            val rows = lengths.take(limit).map { lengthRow ->
                queries.selectImportChildBySequence(
                    scope.accountId,
                    scope.clientId,
                    lengthRow.child_sequence
                ).awaitAsOne()
            }
            InboxReadResult.Success(
                PendingImportChildBatch(rows.map { it.toValidatedPending(limits) }, hasMore)
            )
        }
    }

    @Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod")
    override suspend fun readNextForegroundImportSnapshot(
        scope: InboxScope
    ): InboxReadResult<ForegroundImportSnapshot?> = readOperation {
        if (!scope.isValid() || !scope.isAllowedBy(syntheticOnly)) {
            return@readOperation InboxReadResult.StorageFailure(invalidInput())
        }
        database.transactionWithResult<InboxReadResult<ForegroundImportSnapshot?>> {
            val snapshotMax = queries.selectForegroundImportSnapshotMaxSequence(
                scope.accountId,
                scope.clientId
            ).awaitAsOne().MAX ?: return@transactionWithResult InboxReadResult.Success(null)
            val candidates = queries.selectForegroundImportParentCandidates(
                account_id = scope.accountId,
                client_id = scope.clientId,
                snapshot_max_ingest_sequence = snapshotMax,
                limit = FOREGROUND_PARENT_LOOKAHEAD
            ).awaitAsList()
            val candidate = candidates.firstOrNull() ?: return@transactionWithResult InboxReadResult.Success(null)

            val childMetadata = queries.selectReceiveChildBlobLengthsForParent(
                candidate.ingest_sequence,
                limits.maxChildrenPerEvent.toLong() + 1L
            ).awaitAsList()
            if (
                childMetadata.size > limits.maxChildrenPerEvent ||
                candidate.blob_size <= 0L || candidate.blob_size > limits.maxRawEnvelopeBytes ||
                !childMetadata.map { it.blob_size }.fitBlobBudget(limits.maxDecryptedProtoBytes, childMetadata.size) ||
                !listOf(candidate.blob_size).plus(childMetadata.map { it.blob_size })
                    .fitCombinedForegroundBlobBudget()
            ) {
                return@transactionWithResult InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            }
            val parent = queries.selectRawEventByIngestSequence(
                scope.accountId,
                scope.clientId,
                candidate.ingest_sequence
            ).awaitAsOne()
            if (!parent.isStructurallyValid(limits, syntheticOnly)) {
                return@transactionWithResult InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            }
            val children = childMetadata.map { metadata ->
                queries.selectImportChildBySequence(
                    scope.accountId,
                    scope.clientId,
                    metadata.child_sequence
                ).awaitAsOne().toValidatedPending(limits)
            }
            if (
                children.any {
                    it.scope != scope || it.parentIngestSequence != parent.ingest_sequence ||
                            it.parentServerEventId != parent.server_event_id
                } ||
                children.map { it.itemIndex } != children.indices.toList() ||
                children.isNotEmpty() && parent.receive_state != RawReceiveState.COMPLETED.name ||
                parent.import_state != ForegroundImportState.PENDING.name ||
                children.any { it.importState != ForegroundImportState.PENDING }
            ) {
                return@transactionWithResult InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            }

            val childImports = children.map { child ->
                ForegroundChildImport(
                    child = child,
                    action = child.foregroundImportAction(),
                    recordToken = child.foregroundRecordToken()
                )
            }
            val rawRequired = parent.requiresForegroundRawImport(children.isEmpty(), childImports)
            val rawAction = if (rawRequired) parent.foregroundRawAction(childImports) else null
            val unit = parent.toForegroundImportUnit(rawRequired, rawAction, childImports)
            val snapshotToken = foregroundSnapshotToken(scope, snapshotMax, unit)
            InboxReadResult.Success(
                ForegroundImportSnapshot(
                    protocolVersion = NOTIFICATION_FOREGROUND_IMPORT_PROTOCOL_VERSION,
                    snapshotMaxIngestSequence = snapshotMax,
                    unit = unit,
                    hasMore = candidates.size > 1,
                    snapshotToken = snapshotToken,
                    issuingStore = this@SqlDelightNotificationInboxStore
                )
            )
        }
    }

    @Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod")
    override suspend fun markForegroundImportSnapshotImported(
        snapshot: ForegroundImportSnapshot,
        rawDisposition: ForegroundRawImportDisposition?
    ): ForegroundImportMarkResult = foregroundMarkOperation {
        if (
            snapshot.issuingStore !== this ||
            snapshot.protocolVersion != NOTIFICATION_FOREGROUND_IMPORT_PROTOCOL_VERSION ||
            snapshot.snapshotMaxIngestSequence < snapshot.unit.parentIngestSequence ||
            snapshot.snapshotToken != foregroundSnapshotToken(
                snapshot.unit.scope,
                snapshot.snapshotMaxIngestSequence,
                snapshot.unit
            ) ||
            (snapshot.unit.rawImport == null) != (rawDisposition == null)
        ) {
            return@foregroundMarkOperation ForegroundImportMarkResult.IntegrityConflict
        }
        if (!rawDisposition.isAllowedFor(snapshot.unit.rawImport?.action)) {
            return@foregroundMarkOperation ForegroundImportMarkResult.IntegrityConflict
        }

        database.transactionWithResult<ForegroundImportMarkResult> {
            val unit = snapshot.unit
            val parent = queries.selectRawEventByIngestSequence(
                unit.scope.accountId,
                unit.scope.clientId,
                unit.parentIngestSequence
            ).awaitAsOneOrNull() ?: return@transactionWithResult ForegroundImportMarkResult.IntegrityConflict
            if (!parent.isStructurallyValid(limits, syntheticOnly) || !parent.matchesForegroundUnitIdentity(unit)) {
                return@transactionWithResult ForegroundImportMarkResult.IntegrityConflict
            }

            val childMetadata = queries.selectReceiveChildBlobLengthsForParent(
                parent.ingest_sequence,
                limits.maxChildrenPerEvent.toLong() + 1L
            ).awaitAsList()
            if (childMetadata.size != unit.children.size) {
                return@transactionWithResult ForegroundImportMarkResult.IntegrityConflict
            }
            val currentChildren = childMetadata.map { metadata ->
                queries.selectImportChildBySequence(
                    unit.scope.accountId,
                    unit.scope.clientId,
                    metadata.child_sequence
                ).awaitAsOne().toValidatedPending(limits)
            }
            val exactChildren = currentChildren.zip(unit.children).all { (current, captured) ->
                current.scope == unit.scope && current.parentServerEventId == unit.parentServerEventId &&
                        current.childSequence == captured.child.childSequence &&
                        current.foregroundRecordToken() == captured.recordToken
            }
            if (!exactChildren) {
                return@transactionWithResult ForegroundImportMarkResult.IntegrityConflict
            }

            val allPending = parent.import_state == ForegroundImportState.PENDING.name &&
                    currentChildren.all { it.importState == ForegroundImportState.PENDING }
            val allImported = parent.import_state == ForegroundImportState.IMPORTED.name &&
                    currentChildren.all { it.importState == ForegroundImportState.IMPORTED }
            if (allImported) {
                return@transactionWithResult if (parent.matchesImportedLifecycle(unit, rawDisposition)) {
                    ForegroundImportMarkResult.AlreadyImported
                } else {
                    ForegroundImportMarkResult.IntegrityConflict
                }
            }
            if (!allPending) return@transactionWithResult ForegroundImportMarkResult.IntegrityConflict
            if (
                parent.foregroundParentRecordToken(unit.rawImport != null, unit.rawImport?.action) !=
                unit.parentRecordToken
            ) {
                return@transactionWithResult ForegroundImportMarkResult.IntegrityConflict
            }

            currentChildren.forEach { child ->
                queries.markForegroundChildImportedExact(
                    account_id = unit.scope.accountId,
                    client_id = unit.scope.clientId,
                    child_sequence = child.childSequence,
                    parent_ingest_sequence = child.parentIngestSequence,
                    item_index = child.itemIndex.toLong(),
                    idempotency_key = child.idempotencyKey,
                    decrypted_proto_sha256 = child.decryptedProtoSha256
                )
                if (queries.selectChanges().awaitAsOne() != 1L) throw AbortForegroundImportConflict()
            }

            if (rawDisposition == null) {
                queries.markForegroundRawImportedExact(
                    account_id = unit.scope.accountId,
                    client_id = unit.scope.clientId,
                    ingest_sequence = unit.parentIngestSequence,
                    server_event_id = unit.parentServerEventId,
                    raw_envelope_sha256 = unit.rawEnvelopeSha256
                )
            } else {
                val transition = parent.foregroundRawTransition(rawDisposition)
                queries.markForegroundRawImportedWithDispositionExact(
                    next_receive_state = transition.receiveState.name,
                    next_foreground_recovery_required = transition.foregroundRecoveryRequired.toLong(),
                    next_recovery_reason = transition.recoveryReason,
                    account_id = unit.scope.accountId,
                    client_id = unit.scope.clientId,
                    ingest_sequence = unit.parentIngestSequence,
                    server_event_id = unit.parentServerEventId,
                    raw_envelope_sha256 = unit.rawEnvelopeSha256,
                    expected_receive_state = parent.receive_state,
                    expected_foreground_recovery_required = parent.foreground_recovery_required,
                    expected_recovery_reason = parent.recovery_reason
                )
            }
            if (queries.selectChanges().awaitAsOne() != 1L) throw AbortForegroundImportConflict()
            ForegroundImportMarkResult.Marked(markedRawParentCount = 1, markedChildCount = currentChildren.size)
        }
    }

    override fun close() {
        if (closed.exchange(CLOSED_STATE) == CLOSED_STATE) return
        runCatching { driver.close() }
    }

    private suspend fun <T> readOperation(block: suspend () -> InboxReadResult<T>): InboxReadResult<T> =
        withContext(dispatcher) {
            if (closed.load() == CLOSED_STATE) {
                return@withContext InboxReadResult.StorageFailure(NotificationInboxFailure.CLOSED)
            }
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IllegalArgumentException) {
                InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            } catch (_: Throwable) {
                InboxReadResult.StorageFailure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
            }
        }

    private suspend fun rawWriteOperation(block: suspend () -> RawEventStageResult): RawEventStageResult =
        withContext(dispatcher) {
            if (closed.load() == CLOSED_STATE) {
                return@withContext RawEventStageResult.StorageFailure(NotificationInboxFailure.CLOSED)
            }
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                RawEventStageResult.StorageFailure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
            }
        }

    private suspend fun childWriteOperation(block: suspend () -> ReceiveChildrenStageResult): ReceiveChildrenStageResult =
        withContext(dispatcher) {
            if (closed.load() == CLOSED_STATE) {
                return@withContext ReceiveChildrenStageResult.StorageFailure(NotificationInboxFailure.CLOSED)
            }
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: AbortChildBatchForIntegrityConflict) {
                ReceiveChildrenStageResult.IntegrityConflict
            } catch (_: Throwable) {
                ReceiveChildrenStageResult.StorageFailure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
            }
        }

    private suspend fun mutationOperation(block: suspend () -> InboxMutationResult): InboxMutationResult =
        withContext(dispatcher) {
            if (closed.load() == CLOSED_STATE) {
                return@withContext InboxMutationResult.StorageFailure(NotificationInboxFailure.CLOSED)
            }
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                InboxMutationResult.StorageFailure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
            }
        }

    private suspend fun foregroundMarkOperation(
        block: suspend () -> ForegroundImportMarkResult
    ): ForegroundImportMarkResult = withContext(dispatcher) {
        if (closed.load() == CLOSED_STATE) {
            return@withContext ForegroundImportMarkResult.StorageFailure(NotificationInboxFailure.CLOSED)
        }
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: AbortForegroundImportConflict) {
            ForegroundImportMarkResult.IntegrityConflict
        } catch (_: IllegalArgumentException) {
            ForegroundImportMarkResult.IntegrityConflict
        } catch (_: Throwable) {
            ForegroundImportMarkResult.StorageFailure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
        }
    }

    private fun readValidationFailure(scope: InboxScope, limit: Int): NotificationInboxFailure? = when {
        !scope.isValid() || !scope.isAllowedBy(syntheticOnly) -> invalidInput()
        limit <= 0 -> invalidInput()
        limit > limits.maxRowsPerRead || limit == Int.MAX_VALUE -> limitExceeded()
        else -> null
    }

    private fun mutationValidationFailure(
        scope: InboxScope,
        serverEventId: String,
        reason: String? = null
    ): NotificationInboxFailure? = when {
        !scope.isValid() || !scope.isAllowedBy(syntheticOnly) -> invalidInput()
        !serverEventId.isValidIdentifier() -> invalidInput()
        reason != null && !reason.isValidReason() -> invalidInput()
        else -> null
    }

    private fun RawEventWrite.validationFailure(bytes: ByteArray): NotificationInboxFailure? = when {
        !scope.isValid() || !serverEventId.isValidIdentifier() -> invalidInput()
        bytes.isEmpty() -> invalidInput()
        bytes.size > limits.maxRawEnvelopeBytes -> limitExceeded()
        rawEnvelopeFormatVersion != NOTIFICATION_RAW_ENVELOPE_FORMAT_VERSION -> invalidInput()
        !isTransient && associatedCursor == null -> invalidInput()
        associatedCursor != null && !associatedCursor.isValidCursor() -> invalidInput()
        serverTimestampEpochMillis != null && serverTimestampEpochMillis < 0 -> invalidInput()
        receivedAtEpochMillis < 0 -> invalidInput()
        else -> null
    }

    @Suppress("CyclomaticComplexMethod")
    private fun ReceiveChildWrite.validationFailure(proto: ByteArray?): NotificationInboxFailure? = when {
        !scope.isValid() || !parentServerEventId.isValidIdentifier() || !idempotencyKey.isValidIdentifier() -> invalidInput()
        !idempotencyKey.hasKnownNamespace() -> invalidInput()
        itemIndex < 0 || retryCount < 0 || retryCount > limits.maxRetryCount -> invalidInput()
        idempotencyKey.startsWith(FALLBACK_KEY_PREFIX) &&
                idempotencyKey != fallbackChildIdempotencyKey(parentServerEventId, itemIndex) -> invalidInput()
        idempotencyKey.startsWith(PROTOCOL_MESSAGE_UID_KEY_PREFIX) &&
                idempotencyKey.length == PROTOCOL_MESSAGE_UID_KEY_PREFIX.length -> invalidInput()
        conversationId != null && !conversationId.isValidIdentifier() -> invalidInput()
        senderId != null && !senderId.isValidIdentifier() -> invalidInput()
        senderClientId != null && !senderClientId.isValidIdentifier() -> invalidInput()
        proto != null && proto.size > limits.maxDecryptedProtoBytes -> limitExceeded()
        messageTimestampEpochMillis != null && messageTimestampEpochMillis < 0 -> invalidInput()
        failureClassification != null && !failureClassification.isValidReason() -> invalidInput()
        receiveClassification == ReceiveClassification.APPLICATION_MESSAGE && proto == null -> invalidInput()
        receiveClassification == ReceiveClassification.APPLICATION_MESSAGE &&
                (decryptionState != DecryptionState.DECRYPTED || !cryptoStateApplied) -> invalidInput()
        decryptionState == DecryptionState.DECRYPTED && proto == null -> invalidInput()
        decryptionState == DecryptionState.DECRYPTED && !cryptoStateApplied -> invalidInput()
        decryptionState == DecryptionState.HANDSHAKE_APPLIED && !cryptoStateApplied -> invalidInput()
        decryptionState == DecryptionState.PENDING || decryptionState == DecryptionState.FAILED_RETRYABLE ||
                decryptionState == DecryptionState.DEFERRED_TO_APP -> invalidInput()
        decryptionState == DecryptionState.FAILED_TERMINAL && failureClassification == null -> invalidInput()
        importState != ForegroundImportState.PENDING -> invalidInput()
        else -> null
    }

    private fun ReceiveChildrenWrite.validationFailure(): NotificationInboxFailure? = when {
        !scope.isValid() || !scope.isAllowedBy(syntheticOnly) -> invalidInput()
        !parentServerEventId.isValidIdentifier() -> invalidInput()
        children.isEmpty() -> invalidInput()
        children.size > limits.maxChildrenPerEvent -> limitExceeded()
        children.any { it.scope != scope || it.parentServerEventId != parentServerEventId } -> invalidInput()
        children.map(ReceiveChildWrite::itemIndex).toSet().size != children.size -> invalidInput()
        children.map(ReceiveChildWrite::idempotencyKey).toSet().size != children.size -> invalidInput()
        children.map(ReceiveChildWrite::itemIndex).sorted() != children.indices.toList() -> invalidInput()
        else -> null
    }

    private fun InboxScope.isValid(): Boolean = accountId.isValidIdentifier() && clientId.isValidIdentifier()

    private fun String.isValidIdentifier(): Boolean =
        isNotEmpty() && indexOf(NULL_CHARACTER) < 0 && encodeToByteArray().size <= limits.maxIdentifierUtf8Bytes

    private fun String.isValidCursor(): Boolean =
        isNotEmpty() && indexOf(NULL_CHARACTER) < 0 && encodeToByteArray().size <= limits.maxCursorUtf8Bytes

    private fun String.isValidReason(): Boolean =
        isNotEmpty() && indexOf(NULL_CHARACTER) < 0 && encodeToByteArray().size <= limits.maxReasonUtf8Bytes

    private fun NotificationInboxLimits.areValid(): Boolean =
        maxIdentifierUtf8Bytes > 0 && maxCursorUtf8Bytes > 0 && maxReasonUtf8Bytes > 0 &&
                maxRawEnvelopeBytes in 1..NOTIFICATION_INBOX_SCHEMA_MAX_BLOB_BYTES &&
                maxDecryptedProtoBytes in 1..NOTIFICATION_INBOX_SCHEMA_MAX_BLOB_BYTES &&
                maxBatchBlobBytes >= maxOf(maxRawEnvelopeBytes, maxDecryptedProtoBytes).toLong() &&
                maxRowsPerRead > 0 &&
                maxRowsPerRead < Int.MAX_VALUE && maxChildrenPerEvent > 0 && maxRetryCount >= 0

    private fun injectSyntheticFailure(point: SyntheticNotificationInboxFailurePoint) {
        if (
            syntheticOnly && syntheticFailurePoint == point &&
            syntheticFailureArmed.exchange(DISARMED_STATE) == ARMED_STATE
        ) {
            throw SyntheticNotificationInboxRollbackProbeFailure()
        }
    }

    @Suppress("ReturnCount")
    private fun List<Long?>.fitBlobBudget(perBlobLimit: Int, budgetedCount: Int): Boolean {
        var total = 0L
        for ((index, nullableSize) in withIndex()) {
            val size = nullableSize ?: 0L
            if (size < 0 || size > perBlobLimit) return false
            if (index < budgetedCount) {
                if (total > limits.maxBatchBlobBytes - size) return false
                total += size
            }
        }
        return total <= limits.maxBatchBlobBytes
    }

    private fun List<Long?>.fitCombinedForegroundBlobBudget(): Boolean {
        var total = 0L
        for (nullableSize in this) {
            val size = nullableSize ?: 0L
            if (size < 0 || total > limits.maxBatchBlobBytes - size) return false
            total += size
        }
        return total <= limits.maxBatchBlobBytes
    }

    private fun invalidInput(): NotificationInboxFailure = NotificationInboxFailure.INVALID_INPUT

    private fun limitExceeded(): NotificationInboxFailure = NotificationInboxFailure.CONFIGURED_LIMIT_EXCEEDED

    private fun Int.plusOne(): Long = toLong() + 1L
}

internal expect fun sha256LowercaseHex(bytes: ByteArray): String

private fun RawEvent.matches(request: RawEventWrite, bytes: ByteArray, digest: String): Boolean =
    raw_envelope_sha256 == digest &&
            raw_envelope.contentEquals(bytes) &&
            raw_envelope_format_version == request.rawEnvelopeFormatVersion.toLong() &&
            is_transient == request.isTransient.toLong() &&
            associated_cursor == request.associatedCursor

@Suppress("CyclomaticComplexMethod")
private fun SelectReceiveChildByParentItem.matches(
    request: ReceiveChildWrite,
    proto: ByteArray?,
    digest: String?
): Boolean =
    parent_server_event_id == request.parentServerEventId &&
            item_index == request.itemIndex.toLong() &&
            conversation_id == request.conversationId &&
            sender_id == request.senderId &&
            sender_client_id == request.senderClientId &&
            protocol == request.protocol.name &&
            message_timestamp_epoch_millis == request.messageTimestampEpochMillis &&
            decrypted_proto.contentEqualsNullable(proto) &&
            decrypted_proto_sha256 == digest &&
            crypto_state_applied == request.cryptoStateApplied.toLong() &&
            receive_classification == request.receiveClassification.name &&
            failure_classification == request.failureClassification &&
            decryption_state == request.decryptionState.name &&
            notification_state.isSameOrAdvancedFrom(request.notificationState) &&
            import_state.isSameOrAdvancedFrom(request.importState) &&
            retry_count >= request.retryCount.toLong()

private fun RawEvent.toValidatedPending(
    limits: NotificationInboxLimits,
    syntheticOnly: Boolean
): PendingRawEvent {
    if (!isStructurallyValid(limits, syntheticOnly)) throw CorruptNotificationInboxState()
    return PendingRawEvent(
        ingestSequence = ingest_sequence,
        scope = InboxScope(account_id, client_id),
        serverEventId = server_event_id,
        rawEnvelope = raw_envelope,
        rawEnvelopeSha256 = raw_envelope_sha256,
        rawEnvelopeFormatVersion = raw_envelope_format_version.toInt(),
        serverTimestampEpochMillis = server_timestamp_epoch_millis,
        isTransient = is_transient != 0L,
        associatedCursor = associated_cursor,
        deliverySource = RawEnvelopeDeliverySource.valueOf(delivery_source),
        receivedAtEpochMillis = received_at_epoch_millis,
        receiveState = RawReceiveState.valueOf(receive_state),
        foregroundRecoveryRequired = foreground_recovery_required != 0L,
        recoveryReason = recovery_reason,
        importState = ForegroundImportState.valueOf(import_state)
    )
}

@Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod")
private fun SelectImportChildBySequence.toValidatedPending(limits: NotificationInboxLimits): PendingImportChild {
    val computedDigest = decrypted_proto?.let(::sha256LowercaseHex)
    if (
        computedDigest != decrypted_proto_sha256 ||
        !account_id.isValidStoredValue(limits.maxIdentifierUtf8Bytes) ||
        !client_id.isValidStoredValue(limits.maxIdentifierUtf8Bytes) ||
        !parent_server_event_id.isValidStoredValue(limits.maxIdentifierUtf8Bytes) ||
        !idempotency_key.isValidStoredValue(limits.maxIdentifierUtf8Bytes) ||
        !idempotency_key.hasKnownNamespace() ||
        item_index !in 0L until limits.maxChildrenPerEvent.toLong() ||
        (
            idempotency_key.startsWith(FALLBACK_KEY_PREFIX) &&
                    idempotency_key != fallbackChildIdempotencyKey(parent_server_event_id, item_index.toInt())
        ) ||
        conversation_id?.isValidStoredValue(limits.maxIdentifierUtf8Bytes) == false ||
        sender_id?.isValidStoredValue(limits.maxIdentifierUtf8Bytes) == false ||
        sender_client_id?.isValidStoredValue(limits.maxIdentifierUtf8Bytes) == false ||
        decrypted_proto?.size?.let { it > limits.maxDecryptedProtoBytes } == true ||
        failure_classification?.isValidStoredValue(limits.maxReasonUtf8Bytes) == false ||
        message_timestamp_epoch_millis?.let { it < 0 } == true ||
        retry_count !in 0..limits.maxRetryCount.toLong()
    ) {
        throw CorruptNotificationInboxState()
    }
    val parsedProtocol = ReceiveProtocol.valueOf(protocol)
    val parsedClassification = ReceiveClassification.valueOf(receive_classification)
    val parsedDecryption = DecryptionState.valueOf(decryption_state)
    val parsedNotification = NotificationState.valueOf(notification_state)
    val parsedImport = ForegroundImportState.valueOf(import_state)
    if (
        parsedClassification == ReceiveClassification.APPLICATION_MESSAGE && decrypted_proto == null ||
        parsedClassification == ReceiveClassification.APPLICATION_MESSAGE &&
                (parsedDecryption != DecryptionState.DECRYPTED || crypto_state_applied == 0L) ||
        parsedDecryption == DecryptionState.DECRYPTED && (decrypted_proto == null || crypto_state_applied == 0L) ||
        parsedDecryption == DecryptionState.HANDSHAKE_APPLIED && crypto_state_applied == 0L ||
        parsedDecryption == DecryptionState.PENDING || parsedDecryption == DecryptionState.FAILED_RETRYABLE ||
        parsedDecryption == DecryptionState.DEFERRED_TO_APP ||
        parsedDecryption == DecryptionState.FAILED_TERMINAL && failure_classification == null
    ) {
        throw CorruptNotificationInboxState()
    }
    return PendingImportChild(
        childSequence = child_sequence,
        parentIngestSequence = parent_ingest_sequence,
        scope = InboxScope(account_id, client_id),
        parentServerEventId = parent_server_event_id,
        itemIndex = item_index.toInt(),
        idempotencyKey = idempotency_key,
        conversationId = conversation_id,
        senderId = sender_id,
        senderClientId = sender_client_id,
        protocol = parsedProtocol,
        messageTimestampEpochMillis = message_timestamp_epoch_millis,
        decryptedProto = decrypted_proto,
        decryptedProtoSha256 = decrypted_proto_sha256,
        cryptoStateApplied = crypto_state_applied != 0L,
        receiveClassification = parsedClassification,
        failureClassification = failure_classification,
        decryptionState = parsedDecryption,
        notificationState = parsedNotification,
        importState = parsedImport,
        retryCount = retry_count.toInt()
    )
}

private fun PendingImportChild.foregroundImportAction(): ForegroundImportAction = when {
    decryptionState == DecryptionState.FAILED_TERMINAL -> ForegroundImportAction.RECORD_TERMINAL_FAILURE
    receiveClassification == ReceiveClassification.APPLICATION_MESSAGE &&
            conversationId != null && messageTimestampEpochMillis != null ->
        ForegroundImportAction.UPSERT_APPLICATION_MESSAGE
    receiveClassification == ReceiveClassification.APPLICATION_MESSAGE ->
        ForegroundImportAction.SCHEDULE_FOREGROUND_RECOVERY
    receiveClassification == ReceiveClassification.HANDSHAKE_ONLY && cryptoStateApplied ->
        ForegroundImportAction.RECORD_CRYPTO_STATE_ALREADY_APPLIED
    receiveClassification == ReceiveClassification.WELCOME ||
            receiveClassification == ReceiveClassification.UNSUPPORTED ||
            receiveClassification == ReceiveClassification.HANDSHAKE_ONLY ->
        ForegroundImportAction.SCHEDULE_FOREGROUND_RECOVERY
    else -> ForegroundImportAction.RECORD_COMPLETION
}

private fun PendingImportChild.foregroundRecordToken(): String = foregroundFrameDigest(
    FOREGROUND_CHILD_RECORD_PREFIX,
    listOf(
        scope.accountId,
        scope.clientId,
        childSequence.toString(),
        parentIngestSequence.toString(),
        parentServerEventId,
        itemIndex.toString(),
        idempotencyKey,
        conversationId,
        senderId,
        senderClientId,
        protocol.name,
        messageTimestampEpochMillis?.toString(),
        decryptedProtoSha256,
        cryptoStateApplied.toCanonicalBoolean(),
        receiveClassification.name,
        failureClassification,
        decryptionState.name,
        notificationState.name,
        retryCount.toString(),
        foregroundImportAction().name
    )
)

private fun RawEvent.toForegroundImportUnit(
    rawRequired: Boolean,
    rawAction: ForegroundImportAction?,
    children: List<ForegroundChildImport>
): ForegroundImportUnit = ForegroundImportUnit(
    scope = InboxScope(account_id, client_id),
    parentIngestSequence = ingest_sequence,
    parentServerEventId = server_event_id,
    rawEnvelopeSha256 = raw_envelope_sha256,
    rawEnvelopeFormatVersion = raw_envelope_format_version.toInt(),
    serverTimestampEpochMillis = server_timestamp_epoch_millis,
    isTransient = is_transient != 0L,
    associatedCursor = associated_cursor,
    deliverySource = RawEnvelopeDeliverySource.valueOf(delivery_source),
    receivedAtEpochMillis = received_at_epoch_millis,
    receiveState = RawReceiveState.valueOf(receive_state),
    foregroundRecoveryRequired = foreground_recovery_required != 0L,
    recoveryReason = recovery_reason,
    rawImport = if (rawRequired) ForegroundRawImport(raw_envelope, checkNotNull(rawAction)) else null,
    children = children,
    parentRecordToken = foregroundParentRecordToken(rawRequired, rawAction)
)

private fun RawEvent.requiresForegroundRawImport(
    hasNoChildren: Boolean,
    children: List<ForegroundChildImport>
): Boolean = receive_state == RawReceiveState.DEFERRED_TO_APP.name ||
        foreground_recovery_required != 0L || hasNoChildren ||
        children.any { it.action == ForegroundImportAction.SCHEDULE_FOREGROUND_RECOVERY }

@Suppress("ComplexCondition")
private fun RawEvent.foregroundRawAction(children: List<ForegroundChildImport> = emptyList()): ForegroundImportAction =
    if (receive_state == RawReceiveState.PENDING.name ||
        receive_state == RawReceiveState.DEFERRED_TO_APP.name ||
        foreground_recovery_required != 0L ||
        children.any { it.action == ForegroundImportAction.SCHEDULE_FOREGROUND_RECOVERY }
    ) {
        ForegroundImportAction.SCHEDULE_FOREGROUND_RECOVERY
    } else {
        ForegroundImportAction.RECORD_COMPLETION
    }

private fun RawEvent.foregroundParentRecordToken(
    rawRequired: Boolean,
    rawAction: ForegroundImportAction? = null
): String = foregroundParentRecordToken(
    scope = InboxScope(account_id, client_id),
    ingestSequence = ingest_sequence,
    serverEventId = server_event_id,
    rawSha256 = raw_envelope_sha256,
    rawFormatVersion = raw_envelope_format_version.toInt(),
    serverTimestamp = server_timestamp_epoch_millis,
    transient = is_transient != 0L,
    cursor = associated_cursor,
    source = RawEnvelopeDeliverySource.valueOf(delivery_source),
    receivedAt = received_at_epoch_millis,
    receiveState = RawReceiveState.valueOf(receive_state),
    recoveryRequired = foreground_recovery_required != 0L,
    recoveryReason = recovery_reason,
    rawRequired = rawRequired,
    rawAction = if (rawRequired) rawAction ?: foregroundRawAction() else null
)

@Suppress("ComplexCondition")
private fun RawEvent.matchesForegroundUnitIdentity(unit: ForegroundImportUnit): Boolean =
    account_id == unit.scope.accountId && client_id == unit.scope.clientId &&
            ingest_sequence == unit.parentIngestSequence && server_event_id == unit.parentServerEventId &&
            raw_envelope_sha256 == unit.rawEnvelopeSha256 &&
            raw_envelope_format_version == unit.rawEnvelopeFormatVersion.toLong() &&
            server_timestamp_epoch_millis == unit.serverTimestampEpochMillis &&
            is_transient == unit.isTransient.toLong() && associated_cursor == unit.associatedCursor &&
            delivery_source == unit.deliverySource.name && received_at_epoch_millis == unit.receivedAtEpochMillis

private fun RawEvent.matchesImportedLifecycle(
    unit: ForegroundImportUnit,
    disposition: ForegroundRawImportDisposition?
): Boolean {
    if (disposition == null) {
        return receive_state == unit.receiveState.name &&
                foreground_recovery_required == unit.foregroundRecoveryRequired.toLong() &&
                recovery_reason == unit.recoveryReason
    }
    val expected = unit.foregroundRawTransition(disposition)
    return receive_state == expected.receiveState.name &&
            foreground_recovery_required == expected.foregroundRecoveryRequired.toLong() &&
            recovery_reason == expected.recoveryReason
}

private fun ForegroundImportUnit.recomputedParentRecordToken(): String = foregroundParentRecordToken(
    scope = scope,
    ingestSequence = parentIngestSequence,
    serverEventId = parentServerEventId,
    rawSha256 = rawEnvelopeSha256,
    rawFormatVersion = rawEnvelopeFormatVersion,
    serverTimestamp = serverTimestampEpochMillis,
    transient = isTransient,
    cursor = associatedCursor,
    source = deliverySource,
    receivedAt = receivedAtEpochMillis,
    receiveState = receiveState,
    recoveryRequired = foregroundRecoveryRequired,
    recoveryReason = recoveryReason,
    rawRequired = rawImport != null,
    rawAction = rawImport?.action
)

@Suppress("LongParameterList")
private fun foregroundParentRecordToken(
    scope: InboxScope,
    ingestSequence: Long,
    serverEventId: String,
    rawSha256: String,
    rawFormatVersion: Int,
    serverTimestamp: Long?,
    transient: Boolean,
    cursor: String?,
    source: RawEnvelopeDeliverySource,
    receivedAt: Long,
    receiveState: RawReceiveState,
    recoveryRequired: Boolean,
    recoveryReason: String?,
    rawRequired: Boolean,
    rawAction: ForegroundImportAction?
): String = foregroundFrameDigest(
    FOREGROUND_PARENT_RECORD_PREFIX,
    listOf(
        scope.accountId,
        scope.clientId,
        ingestSequence.toString(),
        serverEventId,
        rawSha256,
        rawFormatVersion.toString(),
        serverTimestamp?.toString(),
        transient.toCanonicalBoolean(),
        cursor,
        source.name,
        receivedAt.toString(),
        receiveState.name,
        recoveryRequired.toCanonicalBoolean(),
        recoveryReason,
        rawRequired.toCanonicalBoolean(),
        rawAction?.name
    )
)

private fun foregroundSnapshotToken(
    scope: InboxScope,
    snapshotMaxIngestSequence: Long,
    unit: ForegroundImportUnit
): String = foregroundFrameDigest(
    FOREGROUND_SNAPSHOT_PREFIX,
    buildList {
        add(NOTIFICATION_FOREGROUND_IMPORT_PROTOCOL_VERSION.toString())
        add(scope.accountId)
        add(scope.clientId)
        add(snapshotMaxIngestSequence.toString())
        add(FOREGROUND_INITIAL_AFTER_PARENT_SEQUENCE.toString())
        add(unit.parentIngestSequence.toString())
        add(unit.recomputedParentRecordToken())
        add(unit.children.size.toString())
        unit.children.forEach { child ->
            add(child.child.childSequence.toString())
            add(child.recordToken)
        }
    }
)

private fun foregroundFrameDigest(prefix: String, fields: List<String?>): String {
    val encoded: List<ByteArray?> = buildList {
        add(prefix.encodeToByteArray())
        fields.forEach { add(it?.encodeToByteArray()) }
    }
    val size = encoded.sumOf { (it?.size ?: 0) + FOREGROUND_FRAME_LENGTH_BYTES }
    val frame = ByteArray(size)
    var offset = 0
    encoded.forEach { value ->
        writeForegroundFrameLength(frame, offset, value?.size ?: FOREGROUND_NULL_FIELD_LENGTH)
        offset += FOREGROUND_FRAME_LENGTH_BYTES
        if (value != null) {
            value.copyInto(frame, offset)
            offset += value.size
        }
    }
    return sha256LowercaseHex(frame)
}

@Suppress("MagicNumber")
private fun writeForegroundFrameLength(destination: ByteArray, offset: Int, value: Int) {
    destination[offset] = (value ushr 24).toByte()
    destination[offset + 1] = (value ushr 16).toByte()
    destination[offset + 2] = (value ushr 8).toByte()
    destination[offset + 3] = value.toByte()
}

private fun ForegroundRawImportDisposition?.isAllowedFor(action: ForegroundImportAction?): Boolean = when (action) {
    null -> this == null
    ForegroundImportAction.SCHEDULE_FOREGROUND_RECOVERY ->
        this == ForegroundRawImportDisposition.DURABLY_QUEUED_FOR_FOREGROUND
    ForegroundImportAction.RECORD_COMPLETION ->
        this == ForegroundRawImportDisposition.PROCESSED_BY_FOREGROUND
    else -> false
}

private fun RawEvent.foregroundRawTransition(
    disposition: ForegroundRawImportDisposition
): ForegroundRawTransition = when (disposition) {
    ForegroundRawImportDisposition.PROCESSED_BY_FOREGROUND -> ForegroundRawTransition(
        receiveState = RawReceiveState.COMPLETED,
        foregroundRecoveryRequired = foreground_recovery_required != 0L,
        recoveryReason = recovery_reason
    )
    ForegroundRawImportDisposition.DURABLY_QUEUED_FOR_FOREGROUND -> ForegroundRawTransition(
        receiveState = RawReceiveState.DEFERRED_TO_APP,
        foregroundRecoveryRequired = true,
        recoveryReason = recovery_reason ?: FOREGROUND_DURABLY_QUEUED_REASON
    )
}

private fun ForegroundImportUnit.foregroundRawTransition(
    disposition: ForegroundRawImportDisposition
): ForegroundRawTransition = when (disposition) {
    ForegroundRawImportDisposition.PROCESSED_BY_FOREGROUND -> ForegroundRawTransition(
        receiveState = RawReceiveState.COMPLETED,
        foregroundRecoveryRequired = foregroundRecoveryRequired,
        recoveryReason = recoveryReason
    )
    ForegroundRawImportDisposition.DURABLY_QUEUED_FOR_FOREGROUND -> ForegroundRawTransition(
        receiveState = RawReceiveState.DEFERRED_TO_APP,
        foregroundRecoveryRequired = true,
        recoveryReason = recoveryReason ?: FOREGROUND_DURABLY_QUEUED_REASON
    )
}

private fun Boolean.toCanonicalBoolean(): String = if (this) "1" else "0"

private data class ForegroundRawTransition(
    val receiveState: RawReceiveState,
    val foregroundRecoveryRequired: Boolean,
    val recoveryReason: String?
)

@Suppress("ComplexCondition", "CyclomaticComplexMethod")
private fun RawEvent.isStructurallyValid(
    limits: NotificationInboxLimits,
    syntheticOnly: Boolean
): Boolean =
    ingest_sequence > 0 &&
            account_id.isValidStoredValue(limits.maxIdentifierUtf8Bytes) &&
            client_id.isValidStoredValue(limits.maxIdentifierUtf8Bytes) &&
            (
                !syntheticOnly || (
                        account_id == SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID &&
                                client_id == SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
                        )
            ) &&
            server_event_id.isValidStoredValue(limits.maxIdentifierUtf8Bytes) &&
            raw_envelope.isNotEmpty() && raw_envelope.size <= limits.maxRawEnvelopeBytes &&
            raw_envelope_sha256 == sha256LowercaseHex(raw_envelope) &&
            raw_envelope_format_version == NOTIFICATION_RAW_ENVELOPE_FORMAT_VERSION.toLong() &&
            server_timestamp_epoch_millis?.let { it < 0 } != true &&
            (is_transient == 0L || is_transient == 1L) &&
            (is_transient != 0L || associated_cursor != null) &&
            associated_cursor?.isValidStoredValue(limits.maxCursorUtf8Bytes) != false &&
            runCatching { RawEnvelopeDeliverySource.valueOf(delivery_source) }.isSuccess &&
            (!syntheticOnly || delivery_source == RawEnvelopeDeliverySource.SYNTHETIC_FEASIBILITY.name) &&
            received_at_epoch_millis >= 0 &&
            ingestion_state == INGESTION_STATE_RAW_STORED &&
            runCatching { RawReceiveState.valueOf(receive_state) }.isSuccess &&
            (foreground_recovery_required == 0L || foreground_recovery_required == 1L) &&
            recovery_reason?.isValidStoredValue(limits.maxReasonUtf8Bytes) != false &&
            runCatching { ForegroundImportState.valueOf(import_state) }.isSuccess

private fun String.isValidStoredValue(maxUtf8Bytes: Int): Boolean =
    isNotEmpty() && indexOf(NULL_CHARACTER) < 0 && encodeToByteArray().size <= maxUtf8Bytes

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}

private fun Boolean.toLong(): Long = if (this) 1L else 0L

private fun InboxScope.isAllowedBy(syntheticOnly: Boolean): Boolean =
    !syntheticOnly || (
            accountId == SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID &&
                    clientId == SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
            )

private fun String.hasKnownNamespace(): Boolean =
    startsWith(PROTOCOL_MESSAGE_UID_KEY_PREFIX) || startsWith(FALLBACK_KEY_PREFIX)

private fun String.isSameOrAdvancedFrom(initial: NotificationState): Boolean =
    this == initial.name || initial == NotificationState.PENDING && this in FINAL_NOTIFICATION_STATES

private fun String.isSameOrAdvancedFrom(initial: ForegroundImportState): Boolean =
    this == initial.name || initial == ForegroundImportState.PENDING && this == ForegroundImportState.IMPORTED.name

private data class PreparedChild(
    val value: ReceiveChildWrite,
    val proto: ByteArray?,
    val digest: String?
)

private class CorruptNotificationInboxState : IllegalArgumentException()
private class AbortChildBatchForIntegrityConflict : Exception()
private class AbortForegroundImportConflict : Exception()
private class SyntheticNotificationInboxRollbackProbeFailure : Exception()

internal enum class SyntheticNotificationInboxFailurePoint {
    NONE,
    BEFORE_CURSOR_UPSERT,
    BEFORE_PARENT_RECEIVE_COMPLETE
}

private const val RECEIVE_STATE_PENDING = "PENDING"
private const val RECEIVE_STATE_COMPLETED = "COMPLETED"
private const val RECEIVE_STATE_DEFERRED = "DEFERRED_TO_APP"
private const val INGESTION_STATE_RAW_STORED = "RAW_STORED"
internal const val UNBOUND_STORAGE_PROFILE = "UNBOUND_REQUIRES_SECURE_FACTORY"
internal const val SYNTHETIC_PLAINTEXT_STORAGE_PROFILE = "PLAINTEXT_SYNTHETIC_SPIKE_V1"
private const val OPEN_STATE = 0
private const val CLOSED_STATE = 1
private const val ARMED_STATE = 1
private const val DISARMED_STATE = 0
private const val NULL_CHARACTER = '\u0000'
private const val FOREGROUND_PARENT_LOOKAHEAD = 2L
private const val FOREGROUND_INITIAL_AFTER_PARENT_SEQUENCE = 0L
private const val FOREGROUND_FRAME_LENGTH_BYTES = 4
private const val FOREGROUND_NULL_FIELD_LENGTH = -1
private const val FOREGROUND_PARENT_RECORD_PREFIX = "com.wire.kalium.notification-inbox.foreground-parent/v1"
private const val FOREGROUND_CHILD_RECORD_PREFIX = "com.wire.kalium.notification-inbox.foreground-child/v1"
private const val FOREGROUND_SNAPSHOT_PREFIX = "com.wire.kalium.notification-inbox.foreground-snapshot/v1"
private const val FOREGROUND_DURABLY_QUEUED_REASON = "FOREGROUND_IMPORT_DURABLY_QUEUED_V1"

private val FINAL_NOTIFICATION_STATES: Set<String> = setOf(
    NotificationState.PRESENTED.name,
    NotificationState.SUPPRESSED.name,
    NotificationState.FAILED.name
)
