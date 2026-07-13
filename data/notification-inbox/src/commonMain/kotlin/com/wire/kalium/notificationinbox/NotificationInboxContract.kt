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
 * Versioned cross-language contract for the app/NSE handoff store.
 *
 * Raw envelope format v1 is the UTF-8 JSON serialization of the complete `data.event` JSON value,
 * with every unknown field retained. The consumable transport wrapper, delivery tag, and sync
 * marker are excluded. The bytes must be captured before DTO decoding and are stored exactly; this
 * module does not parse or re-serialize them. Any byte difference under one scoped event ID is an
 * integrity conflict. M7 must prove the transport adapter can capture this representation.
 *
 * Children are ordered by parent ingest sequence, then caller-assigned item index, then idempotency
 * key. A protocol message UID uses the `message-uid:v1:` namespace. Otherwise both native and
 * Kotlin producers use [fallbackChildIdempotencyKey].
 *
 * The spike has no automatic retention, eviction, or database recreation. Foreground code may run
 * the explicit bounded cleanup operation while holding the account lock; it deletes only complete
 * imported parents after a caller-supplied retention cutoff and preserves the durable-cursor
 * source. Callers supply per-blob/read/child limits and account for the database plus SQLite
 * sidecars before write admission. SQLITE_FULL is a storage failure and the transport event must
 * not be acknowledged. Production storage caps and reserves, retention timing, corruption recovery
 * UX, and downgrade behavior remain release decisions.
 * Corrupt or incompatible state fails closed.
 *
 * This store is not a synchronization primitive. App and NSE operations are valid only while the
 * caller owns the Milestone 5 per-account process lock.
 */
@Suppress("TooManyFunctions")
public interface NotificationInboxStore {
    /**
     * One-time foreground-owned cutover to the shared cursor authority.
     *
     * This must run while the account lock is held and before either process starts shared sync.
     * Exact replay is idempotent; a different cutover identity or seed fails closed. There is no
     * API that silently restores the legacy cursor as an authority.
     */
    public suspend fun prepareSharedCursor(request: SharedCursorPreparation): SharedCursorPreparationResult

    /**
     * Activates only the exact prepared cutover after the native app has durably stopped legacy
     * sync and recorded the same [cutoverId]. A crash can leave both synchronizers disabled, never
     * both authoritative.
     */
    public suspend fun activatePreparedSharedCursor(
        scope: InboxScope,
        cutoverId: String
    ): SharedCursorActivationResult

    public suspend fun readCursor(scope: InboxScope): InboxReadResult<DurableInboxCursor?>

    /**
     * Inserts or verifies the raw event and advances a non-transient cursor in one transaction.
     * A transient event is committed without changing the durable cursor.
     */
    public suspend fun stageRawEvent(request: RawEventWrite): RawEventStageResult

    public suspend fun readPendingReceive(
        scope: InboxScope,
        limit: Int
    ): InboxReadResult<PendingRawEventBatch>

    /** Includes unknown and non-message parents even when no receive child exists. */
    public suspend fun readPendingRawImport(
        scope: InboxScope,
        limit: Int
    ): InboxReadResult<PendingRawEventBatch>

    public suspend fun markReceiveCompleted(
        scope: InboxScope,
        serverEventId: String
    ): InboxMutationResult

    public suspend fun deferToForeground(
        scope: InboxScope,
        serverEventId: String,
        reason: String
    ): InboxMutationResult

    /**
     * Compatibility alias for [requireCursorGlobalRecovery]. Contract v2 never records a global
     * recovery signal without atomically disabling shared cursor authority.
     */
    public suspend fun recordGlobalRecovery(
        scope: InboxScope,
        reason: String,
        recordedAtEpochMillis: Long
    ): InboxMutationResult

    /** Stops NSE cursor mutation and records a foreground-owned recovery signal atomically. */
    public suspend fun requireCursorGlobalRecovery(
        scope: InboxScope,
        reason: String,
        recordedAtEpochMillis: Long
    ): InboxMutationResult

    public suspend fun readPendingGlobalRecovery(
        scope: InboxScope,
        limit: Int
    ): InboxReadResult<GlobalRecoveryBatch>

    /** Called only after the native foreground transaction durably records the same signal token. */
    public suspend fun acknowledgeGlobalRecovery(
        scope: InboxScope,
        signalToken: String,
        acknowledgedAtEpochMillis: Long
    ): InboxMutationResult

    /**
     * Inserts or exact-verifies one complete deterministic child batch in a single transaction.
     * The parent receive state is completed in that same commit. A conflict aborts the entire
     * batch, and replay must describe the exact complete child set. A receive result with zero
     * children uses [markReceiveCompleted]; its raw parent remains pending for foreground import.
     */
    public suspend fun stageReceiveChildren(request: ReceiveChildrenWrite): ReceiveChildrenStageResult

    /** Read-only foreground handoff surface; importing into the main database is a later milestone. */
    public suspend fun readPendingImportChildren(
        scope: InboxScope,
        limit: Int
    ): InboxReadResult<PendingImportChildBatch>

    /**
     * Reads at most one complete parent import unit. Children are never split across snapshots.
     *
     * The returned snapshot is valid only while the caller retains the Milestone 5 account lock
     * and this store instance. Newer ingest sequences cannot enter the captured boundary.
     */
    public suspend fun readNextForegroundImportSnapshot(
        scope: InboxScope
    ): InboxReadResult<ForegroundImportSnapshot?>

    /**
     * Post-main-database-commit compare-and-set for exactly [snapshot].
     *
     * A raw unit requires [rawDisposition]. Child-only units reject one. The entire parent is
     * validated before any row changes, and all changes share one handoff transaction.
     */
    public suspend fun markForegroundImportSnapshotImported(
        snapshot: ForegroundImportSnapshot,
        rawDisposition: ForegroundRawImportDisposition?,
        importedAtEpochMillis: Long
    ): ForegroundImportMarkResult

    /**
     * Deletes only fully imported parents older than the retention cutoff, in deterministic order.
     * The current durable-cursor source is never eligible. The caller retains the account lock and
     * supplies a bounded row count. This foreground maintenance API has no internal deadline; its
     * host must invoke it only when enough foreground execution time remains.
     */
    public suspend fun cleanupImported(request: NotificationInboxCleanupRequest): NotificationInboxCleanupResult

    /**
     * Atomically persists a permanent removal tombstone and logically deletes scoped inbox rows.
     *
     * The tombstone database and stable lock entry are retained so a stale NSE cannot recreate the
     * removed scope. This does not prove physical erasure from SQLite free pages. Reusing the same
     * account/client identity requires an explicit later contract, not deletion of this marker.
     */
    public suspend fun tombstoneAccount(request: NotificationInboxAccountRemoval): NotificationInboxRemovalResult

    /** Must be idempotent and non-throwing. */
    public fun close()
}

public data class InboxScope(
    public val accountId: String,
    public val clientId: String
)

/** Required construction limits; production values need explicit product and security approval. */
public data class NotificationInboxLimits(
    public val maxIdentifierUtf8Bytes: Int,
    public val maxCursorUtf8Bytes: Int,
    public val maxReasonUtf8Bytes: Int,
    public val maxRawEnvelopeBytes: Int,
    public val maxDecryptedProtoBytes: Int,
    public val maxBatchBlobBytes: Long,
    public val maxRowsPerRead: Int,
    public val maxChildrenPerEvent: Int,
    public val maxRetryCount: Int
)

public data class DurableInboxCursor(
    public val value: String,
    public val sourceIngestSequence: Long?,
    public val sourceServerEventId: String?,
    public val updatedAtEpochMillis: Long,
    public val provenance: InboxCursorProvenance
)

public enum class InboxCursorProvenance {
    LEGACY_SEED,
    STAGED_RAW_EVENT
}

public enum class RawEnvelopeDeliverySource {
    CONSUMABLE_WEBSOCKET,
    PENDING_PAGE,
    FOREGROUND_SYNC,
    SYNTHETIC_FEASIBILITY
}

/** Exact raw envelope write; [rawEnvelope] is copied at construction and on every read. */
@Suppress("LongParameterList")
public class RawEventWrite(
    public val scope: InboxScope,
    public val serverEventId: String,
    rawEnvelope: ByteArray,
    public val rawEnvelopeFormatVersion: Int,
    public val serverTimestampEpochMillis: Long?,
    public val isTransient: Boolean,
    public val associatedCursor: String?,
    public val deliverySource: RawEnvelopeDeliverySource,
    public val receivedAtEpochMillis: Long
) {
    private val ownedRawEnvelope: ByteArray = rawEnvelope.copyOf()

    public val rawEnvelope: ByteArray
        get() = ownedRawEnvelope.copyOf()
}

/** Exact durable raw row, returned in deterministic ingest order. */
@Suppress("LongParameterList")
public class PendingRawEvent(
    public val ingestSequence: Long,
    public val scope: InboxScope,
    public val serverEventId: String,
    rawEnvelope: ByteArray,
    public val rawEnvelopeSha256: String,
    public val rawEnvelopeFormatVersion: Int,
    public val serverTimestampEpochMillis: Long?,
    public val isTransient: Boolean,
    public val associatedCursor: String?,
    public val deliverySource: RawEnvelopeDeliverySource,
    public val receivedAtEpochMillis: Long,
    public val receiveState: RawReceiveState,
    public val foregroundRecoveryRequired: Boolean,
    public val recoveryReason: String?,
    public val importState: ForegroundImportState
) {
    private val ownedRawEnvelope: ByteArray = rawEnvelope.copyOf()

    public val rawEnvelope: ByteArray
        get() = ownedRawEnvelope.copyOf()
}

public enum class RawReceiveState {
    PENDING,
    COMPLETED,
    DEFERRED_TO_APP
}

public class PendingRawEventBatch(
    events: List<PendingRawEvent>,
    public val hasMore: Boolean
) {
    public val events: List<PendingRawEvent> = events.toList()
}

public enum class ReceiveProtocol {
    PROTEUS,
    MLS,
    NONE
}

public enum class ReceiveClassification {
    APPLICATION_MESSAGE,
    HANDSHAKE_ONLY,
    WELCOME,
    UNSUPPORTED,
    NON_MESSAGE_EVENT
}

public enum class DecryptionState {
    NOT_REQUIRED,
    PENDING,
    DECRYPTED,
    HANDSHAKE_APPLIED,
    DEFERRED_TO_APP,
    FAILED_RETRYABLE,
    FAILED_TERMINAL
}

public enum class NotificationState {
    NOT_ELIGIBLE,
    PENDING,
    PRESENTED,
    SUPPRESSED,
    FAILED
}

public enum class ForegroundImportState {
    PENDING,
    IMPORTED
}

/**
 * One child produced by receive-only processing. [itemIndex] is stable within the parent event.
 * [idempotencyKey] is produced by [protocolMessageUidChildIdempotencyKey] or
 * [fallbackChildIdempotencyKey].
 */
@Suppress("LongParameterList")
public class ReceiveChildWrite(
    public val scope: InboxScope,
    public val parentServerEventId: String,
    public val itemIndex: Int,
    public val idempotencyKey: String,
    public val conversationId: String?,
    public val senderId: String?,
    public val senderClientId: String?,
    public val protocol: ReceiveProtocol,
    public val messageTimestampEpochMillis: Long?,
    decryptedProto: ByteArray?,
    public val cryptoStateApplied: Boolean,
    public val receiveClassification: ReceiveClassification,
    public val failureClassification: String?,
    public val decryptionState: DecryptionState,
    public val notificationState: NotificationState,
    public val importState: ForegroundImportState,
    public val retryCount: Int
) {
    private val ownedDecryptedProto: ByteArray? = decryptedProto?.copyOf()

    public val decryptedProto: ByteArray?
        get() = ownedDecryptedProto?.copyOf()
}

public class ReceiveChildrenWrite(
    public val scope: InboxScope,
    public val parentServerEventId: String,
    children: List<ReceiveChildWrite>
) {
    public val children: List<ReceiveChildWrite> = children.toList()
}

/** Exact child row for a later idempotent foreground importer. */
@Suppress("LongParameterList")
public class PendingImportChild(
    public val childSequence: Long,
    public val parentIngestSequence: Long,
    public val scope: InboxScope,
    public val parentServerEventId: String,
    public val itemIndex: Int,
    public val idempotencyKey: String,
    public val conversationId: String?,
    public val senderId: String?,
    public val senderClientId: String?,
    public val protocol: ReceiveProtocol,
    public val messageTimestampEpochMillis: Long?,
    decryptedProto: ByteArray?,
    public val decryptedProtoSha256: String?,
    public val cryptoStateApplied: Boolean,
    public val receiveClassification: ReceiveClassification,
    public val failureClassification: String?,
    public val decryptionState: DecryptionState,
    public val notificationState: NotificationState,
    public val importState: ForegroundImportState,
    public val retryCount: Int
) {
    private val ownedDecryptedProto: ByteArray? = decryptedProto?.copyOf()

    public val decryptedProto: ByteArray?
        get() = ownedDecryptedProto?.copyOf()
}

public class PendingImportChildBatch(
    children: List<PendingImportChild>,
    public val hasMore: Boolean
) {
    public val children: List<PendingImportChild> = children.toList()
}

/** Native main-database action; this is a versioned state mapping, not presentation policy. */
public enum class ForegroundImportAction {
    UPSERT_APPLICATION_MESSAGE,
    RECORD_CRYPTO_STATE_ALREADY_APPLIED,
    RECORD_COMPLETION,
    RECORD_TERMINAL_FAILURE,
    SCHEDULE_FOREGROUND_RECOVERY
}

/**
 * Explicit ownership transfer for a raw row that the native main database committed.
 *
 * `PROCESSED_BY_FOREGROUND` prevents a zero-child `PENDING` row from being received again.
 * `DURABLY_QUEUED_FOR_FOREGROUND` records that the native database owns later processing.
 */
public enum class ForegroundRawImportDisposition {
    PROCESSED_BY_FOREGROUND,
    DURABLY_QUEUED_FOR_FOREGROUND
}

/** Copy-owned raw work exposed only when the native foreground database must assume it. */
public class ForegroundRawImport internal constructor(
    rawEnvelope: ByteArray,
    public val action: ForegroundImportAction
) {
    private val ownedRawEnvelope: ByteArray = rawEnvelope.copyOf()

    public val rawEnvelope: ByteArray
        get() = ownedRawEnvelope.copyOf()
}

public class ForegroundChildImport internal constructor(
    public val child: PendingImportChild,
    public val action: ForegroundImportAction,
    /** Lowercase SHA-256 over the canonical foreground-record frame v1. */
    public val recordToken: String
)

/** One complete parent, ordered by ingest sequence and containing every child in item order. */
@Suppress("LongParameterList")
public class ForegroundImportUnit internal constructor(
    public val scope: InboxScope,
    public val parentIngestSequence: Long,
    public val parentServerEventId: String,
    public val rawEnvelopeSha256: String,
    public val rawEnvelopeFormatVersion: Int,
    public val serverTimestampEpochMillis: Long?,
    public val isTransient: Boolean,
    public val associatedCursor: String?,
    public val deliverySource: RawEnvelopeDeliverySource,
    public val receivedAtEpochMillis: Long,
    public val receiveState: RawReceiveState,
    public val foregroundRecoveryRequired: Boolean,
    public val recoveryReason: String?,
    public val rawImport: ForegroundRawImport?,
    children: List<ForegroundChildImport>,
    /** Lowercase SHA-256 over the canonical foreground-parent frame v1. */
    public val parentRecordToken: String
) {
    public val children: List<ForegroundChildImport> = children.toList()
}

/**
 * One bounded crash-replay unit. The opaque token binds the scope, boundary, and every row token.
 * Generated SQLDelight entities are deliberately absent from this cross-language surface.
 */
public class ForegroundImportSnapshot internal constructor(
    public val protocolVersion: Int,
    public val snapshotMaxIngestSequence: Long,
    public val unit: ForegroundImportUnit,
    public val hasMore: Boolean,
    public val snapshotToken: String,
    internal val issuingStore: Any
)

public sealed interface ForegroundImportMarkResult {
    public data class Marked(
        public val markedRawParentCount: Int,
        public val markedChildCount: Int
    ) : ForegroundImportMarkResult

    public data object AlreadyImported : ForegroundImportMarkResult
    public data object IntegrityConflict : ForegroundImportMarkResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : ForegroundImportMarkResult
}

public sealed interface InboxReadResult<out T> {
    public data class Success<T>(public val value: T) : InboxReadResult<T>
    public data class StorageFailure(public val reason: NotificationInboxFailure) : InboxReadResult<Nothing>
}

public sealed interface RawEventStageResult {
    public data class Inserted(public val ingestSequence: Long) : RawEventStageResult
    public data class ExactDuplicate(public val ingestSequence: Long) : RawEventStageResult
    public data object IntegrityConflict : RawEventStageResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : RawEventStageResult
}

public sealed interface ReceiveChildrenStageResult {
    public data class Stored(
        public val insertedCount: Int,
        public val exactDuplicateCount: Int
    ) : ReceiveChildrenStageResult
    public data object ParentMissing : ReceiveChildrenStageResult
    public data object IntegrityConflict : ReceiveChildrenStageResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : ReceiveChildrenStageResult
}

public sealed interface InboxMutationResult {
    public data object Success : InboxMutationResult
    public data object Missing : InboxMutationResult
    public data object IntegrityConflict : InboxMutationResult
    public data class StorageFailure(public val reason: NotificationInboxFailure) : InboxMutationResult
}

public enum class NotificationInboxFailure {
    INVALID_INPUT,
    CONFIGURED_LIMIT_EXCEEDED,
    INCOMPATIBLE_SCHEMA,
    CORRUPT_STATE,
    STORAGE_UNAVAILABLE,
    ACCOUNT_NOT_ACTIVE,
    ACCOUNT_TOMBSTONED,
    CURSOR_CUTOVER_REQUIRED,
    CURSOR_RECOVERY_REQUIRED,
    CLOSED,
    UNEXPECTED_PLATFORM_FAILURE
}

public const val NOTIFICATION_INBOX_CONTRACT_VERSION: Int = 2
public const val NOTIFICATION_RAW_ENVELOPE_FORMAT_VERSION: Int = 1
public const val NOTIFICATION_CHILD_PAYLOAD_FORMAT_VERSION: Int = 1
public const val NOTIFICATION_FOREGROUND_IMPORT_PROTOCOL_VERSION: Int = 1
public const val NOTIFICATION_INBOX_SCHEMA_MAX_BLOB_BYTES: Int = 1_048_576
public const val SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID: String = "synthetic-notification-inbox-account"
public const val SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID: String = "synthetic-notification-inbox-client"

/** Namespace for a real protocol message UID; prevents collision with fallback keys. */
public fun protocolMessageUidChildIdempotencyKey(messageUid: String): String =
    "$PROTOCOL_MESSAGE_UID_KEY_PREFIX$messageUid"

/**
 * Domain-separated deterministic fallback for a child without a protocol message UID.
 *
 * SHA-256 input is UTF-8 `com.wire.kalium.notification-inbox-child/v1`, a four-byte big-endian
 * server-event-ID byte length, those UTF-8 bytes, then a four-byte big-endian non-negative item
 * index. The returned key is `fallback-v1:` plus lowercase hexadecimal SHA-256.
 *
 * Known vector: event ID `synthetic-event-a`, item index `0` produces
 * `fallback-v1:e4c406f63d2889e24cb1b69da20d1298bbe9d4494022ff33edd9f98423f3a155`.
 */
public fun fallbackChildIdempotencyKey(serverEventId: String, itemIndex: Int): String {
    require(serverEventId.isNotEmpty() && itemIndex >= 0)
    val eventBytes = serverEventId.encodeToByteArray()
    val prefixBytes = CHILD_FALLBACK_DIGEST_PREFIX.encodeToByteArray()
    val digestInput = ByteArray(prefixBytes.size + LENGTH_FIELD_BYTES + eventBytes.size + LENGTH_FIELD_BYTES)
    prefixBytes.copyInto(digestInput)
    writeBigEndianInt(digestInput, prefixBytes.size, eventBytes.size)
    eventBytes.copyInto(digestInput, prefixBytes.size + LENGTH_FIELD_BYTES)
    writeBigEndianInt(digestInput, digestInput.size - LENGTH_FIELD_BYTES, itemIndex)
    return "$FALLBACK_KEY_PREFIX${sha256LowercaseHex(digestInput)}"
}

@Suppress("MagicNumber")
private fun writeBigEndianInt(destination: ByteArray, offset: Int, value: Int) {
    destination[offset] = (value ushr 24).toByte()
    destination[offset + 1] = (value ushr 16).toByte()
    destination[offset + 2] = (value ushr 8).toByte()
    destination[offset + 3] = value.toByte()
}

internal const val CHILD_FALLBACK_DIGEST_PREFIX: String = "com.wire.kalium.notification-inbox-child/v1"
internal const val PROTOCOL_MESSAGE_UID_KEY_PREFIX: String = "message-uid:v1:"
internal const val FALLBACK_KEY_PREFIX: String = "fallback-v1:"
private const val LENGTH_FIELD_BYTES: Int = 4
