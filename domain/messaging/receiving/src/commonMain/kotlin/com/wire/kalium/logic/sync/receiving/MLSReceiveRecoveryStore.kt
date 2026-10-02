/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.event.NotificationEventStore
import kotlin.io.encoding.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/** Identity of a receive operation, not a copy of the group's current crypto state. */
@Serializable
data class PendingMLSReceiveRecovery(
    @SerialName("eventId") val eventId: String,
    @SerialName("groupId") val groupId: String,
    @SerialName("ciphertextHash") val ciphertextHash: String,
) {
    val id: String get() = KtxSerializer.json.encodeToString(this).encodeUtf8().sha256().hex()
}

/**
 * Owned by one exclusively leased Apple account/client. Reads operation and receipt identities once
 * per owner and after an uncertain SQL write; ordinary messages need no per-event query.
 * Callers must write intent before POST and acknowledge only after native commit and effects.
 * Records protect retries of the same durable event, not ciphertext delivered under a new
 * event identity after the original has been acknowledged.
 */
@Suppress("TooManyFunctions")
class MLSReceiveRecoveryStore(
    private val metadata: MetadataDAO,
    private val events: NotificationEventStore,
    clientId: String,
) {
    private val clientHash = clientId.encodeUtf8().sha256().hex()
    private val prefix = "mls_receive_recovery_v2:$clientHash:"
    private val until = "mls_receive_recovery_v2:$clientHash;"
    private val mutex = Mutex()
    private var pending: PendingIndex? = null

    init {
        require(clientId.isNotBlank())
    }

    /** The payload is decoded/hashed only when this event has an outstanding operation. */
    suspend fun find(eventId: String, ciphertext: () -> ByteArray): PendingMLSReceiveRecovery? = mutex.withLock {
        val candidates = load().operations.values.filter { it.eventId == eventId }
        if (candidates.isEmpty()) return@withLock null
        val hash = ciphertext().toByteString().sha256().hex()
        candidates.firstOrNull { it.ciphertextHash == hash }
    }

    suspend fun begin(eventId: String, groupId: String, ciphertext: ByteArray): PendingMLSReceiveRecovery = mutex.withLock {
        val entries = load()
        val operation = operation(eventId, groupId, ciphertext)
        check(
            entries.operations.values.none {
                it.eventId == eventId && it.ciphertextHash == operation.ciphertextHash && it.groupId != groupId
            }
        ) { "Recovery operation belongs to a different MLS group" }
        val key = prefix + operation.id
        if (entries.operations[key] != null) return@withLock entries.operations.getValue(key)
        uncertainWrite {
            metadata.insertValue(KtxSerializer.json.encodeToString(operation), key)
            pending = entries.copy(operations = entries.operations + (key to operation))
        }
        operation
    }

    /** Read before a POST as well as on retry, so an unsupported native format never follows a new remote mutation. */
    suspend fun isCompleted(context: MlsCoreCryptoContext, operation: PendingMLSReceiveRecovery): Boolean = mutex.withLock {
        requirePending(operation)
        val joinedEpoch = readCheckpoint(context).completed[operation.id] ?: return@withLock false
        context.conversationExists(operation.groupId) && context.conversationEpoch(operation.groupId) >= joinedEpoch
    }

    /** Must run in the same native transaction as the successful, explicitly verified external join. */
    suspend fun complete(context: MlsCoreCryptoContext, operation: PendingMLSReceiveRecovery) = mutex.withLock {
        val entries = requirePending(operation)
        val checkpoint = readCheckpoint(context)
        val outstanding = entries.operations.values.map { it.id }.toSet()
        val joinedEpoch = context.conversationEpoch(operation.groupId)
        check(joinedEpoch > 0uL) { "An external join must advance the group epoch" }
        val completed = checkpoint.completed.filterKeys { it in outstanding } + (operation.id to joinedEpoch)
        val processed = checkpoint.processed.intersect(entries.receipts.values.map { it.id }.toSet())
        writeCheckpoint(context, checkpoint.copy(completed = completed, processed = processed))
    }

    /** A receipt proves persisted payload effects, independently of the group's current epoch. */
    suspend fun wasProcessed(context: MlsCoreCryptoContext, event: Event.Conversation.NewMLSMessage): Boolean = mutex.withLock {
        val candidates = load().receipts.values.filter { it.eventId == event.id }
        if (candidates.isEmpty()) return@withLock false
        val receipt = receipt(event)
        if (receipt !in candidates) return@withLock false
        receipt.id in readCheckpoint(context).processed
    }

    /** Proves SQL effects and MLS changes, but never proves an outer Proteus transaction committed. */
    suspend fun wereEffectsProcessed(context: MlsCoreCryptoContext, payload: ReceivedPayloadIdentity): Boolean = mutex.withLock {
        val candidates = load().receipts.values.filter { it.eventId == payload.eventId }
        if (candidates.isEmpty()) return@withLock false
        val receipt = effectReceipt(payload)
        receipt in candidates && receipt.id in readCheckpoint(context).processed
    }

    /** Call after every payload's effects and their flush, within the enclosing native transaction. */
    suspend fun recordProcessed(
        context: MlsCoreCryptoContext,
        processedEvents: List<Event.Conversation.NewMLSMessage>,
        processedPayloads: List<ReceivedPayloadIdentity> = emptyList(),
    ): Unit = mutex.withLock {
        val entries = load()
        val eventIds = processedEvents.map { it.id }.toSet()
        val candidates = entries.operations.values.filter { it.eventId in eventIds }
        if (candidates.isEmpty()) return@withLock
        val matching = processedEvents.flatMap { event ->
            val operations = candidates.filter { it.eventId == event.id }
            if (operations.isEmpty()) emptyList() else {
                val hash = Base64.decode(event.content).toByteString().sha256().hex()
                operations.filter { it.ciphertextHash == hash }
            }
        }
        if (matching.isEmpty()) return@withLock
        val checkpoint = readCheckpoint(context)
        check(matching.all { it.id in checkpoint.completed }) { "Payload receipts require completed recovery operations" }
        val receipts = (processedEvents.map(::receipt) + processedPayloads.map(::effectReceipt))
            .associateBy { prefix + PAYLOAD + it.id }
        val updated = entries.copy(receipts = entries.receipts + receipts)
        uncertainWrite {
            receipts.filterKeys { it !in entries.receipts }.forEach { (key, receipt) ->
                metadata.insertValue(KtxSerializer.json.encodeToString(receipt), key)
            }
            val completed = checkpoint.completed.filterKeys { id -> entries.operations.values.any { it.id == id } }
            val outstanding = updated.receipts.values.map { it.id }.toSet()
            val processed = checkpoint.processed.intersect(outstanding) + receipts.values.map { it.id }
            writeCheckpoint(context, checkpoint.copy(completed = completed, processed = processed))
            pending = updated
        }
    }

    /** SQL retirement and queue markers commit together; no native completion is inferred from an epoch. */
    suspend fun acknowledge(eventIds: List<String>) = mutex.withLock {
        val entries = load()
        val ids = eventIds.toSet()
        val operations = entries.operations.filterValues { it.eventId in ids }
        val receipts = entries.receipts.filterValues { it.eventId in ids }
        val keysByEvent = (operations.map { it.value.eventId to it.key } + receipts.map { it.value.eventId to it.key })
            .groupBy({ it.first }, { it.second })
        uncertainWrite {
            events.markProcessed(eventIds, keysByEvent)
            pending = PendingIndex(entries.operations - operations.keys, entries.receipts - receipts.keys)
        }
    }

    private suspend fun load(): PendingIndex {
        pending?.let { return it }
        val operations = mutableMapOf<String, PendingMLSReceiveRecovery>()
        val receipts = mutableMapOf<String, PayloadReceipt>()
        metadata.valuesByKeyRange(prefix, until).forEach { (key, value) ->
            if (key.startsWith(prefix + PAYLOAD)) {
                val receipt = KtxSerializer.json.decodeFromString<PayloadReceipt>(value)
                check(receipt.eventId.isNotBlank() && HASH.matches(receipt.payloadHash))
                check(key == prefix + PAYLOAD + receipt.id) { "Payload receipt identity does not match its key" }
                receipts[key] = receipt
            } else {
                val operation = KtxSerializer.json.decodeFromString<PendingMLSReceiveRecovery>(value)
                check(operation.eventId.isNotBlank() && operation.groupId.isNotBlank() && HASH.matches(operation.ciphertextHash))
                check(key == prefix + operation.id) { "Recovery operation identity does not match its key" }
                operations[key] = operation
            }
        }
        return PendingIndex(operations, receipts).also { pending = it }
    }

    private suspend fun requirePending(operation: PendingMLSReceiveRecovery): PendingIndex = load().also {
        check(it.operations[prefix + operation.id] == operation) {
            "Recovery operation is not pending for this client"
        }
    }

    private suspend fun readCheckpoint(context: MlsCoreCryptoContext): RecoveryCheckpoint {
        val bytes = context.readTransactionCheckpoint() ?: return RecoveryCheckpoint(FORMAT, clientHash, emptyMap())
        return KtxSerializer.json.decodeFromString<RecoveryCheckpoint>(bytes.decodeToString()).also {
            check(it.format == FORMAT && it.clientHash == clientHash) { "Unsupported native receive checkpoint" }
            check(it.completed.keys.all(HASH::matches) && it.processed.all(HASH::matches)) {
                "Invalid native receive checkpoint identity"
            }
            check(it.completed.values.all { epoch -> epoch > 0uL }) { "Invalid native completion epoch" }
        }
    }

    private suspend fun writeCheckpoint(context: MlsCoreCryptoContext, checkpoint: RecoveryCheckpoint) =
        context.writeTransactionCheckpoint(KtxSerializer.json.encodeToString(checkpoint).encodeToByteArray())

    @Suppress("TooGenericExceptionCaught") // A SQL commit may precede cancellation at the dispatcher boundary.
    private suspend fun uncertainWrite(block: suspend () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            pending = null
            throw failure
        }
    }

    private fun operation(eventId: String, groupId: String, ciphertext: ByteArray): PendingMLSReceiveRecovery {
        require(eventId.isNotBlank() && groupId.isNotBlank())
        return PendingMLSReceiveRecovery(eventId, groupId, ciphertext.toByteString().sha256().hex())
    }

    private fun receipt(event: Event.Conversation.NewMLSMessage): PayloadReceipt {
        require(event.id.isNotBlank())
        val payload = PayloadFingerprint(
            event.conversationId,
            event.subconversationId?.value,
            event.senderUserId,
            event.messageInstant.toString(),
            event.content,
        )
        return PayloadReceipt(event.id, KtxSerializer.json.encodeToString(payload).encodeUtf8().sha256().hex())
    }

    private fun effectReceipt(payload: ReceivedPayloadIdentity): PayloadReceipt {
        require(payload.eventId.isNotBlank())
        return PayloadReceipt(payload.eventId, ("event-content-v1:" + payload.serialized()).encodeUtf8().sha256().hex())
    }

    private data class PendingIndex(
        val operations: Map<String, PendingMLSReceiveRecovery>,
        val receipts: Map<String, PayloadReceipt>,
    )

    @Serializable
    private data class PayloadReceipt(
        @SerialName("eventId") val eventId: String,
        @SerialName("payloadHash") val payloadHash: String,
    ) {
        val id: String get() = KtxSerializer.json.encodeToString(this).encodeUtf8().sha256().hex()
    }

    @Serializable
    private data class PayloadFingerprint(
        @SerialName("conversationId") val conversationId: QualifiedID,
        @SerialName("subconversationId") val subconversationId: String?,
        @SerialName("senderUserId") val senderUserId: QualifiedID,
        @SerialName("messageInstant") val messageInstant: String,
        @SerialName("content") val content: String,
    )

    @Serializable
    private data class RecoveryCheckpoint(
        @SerialName("format") val format: String,
        @SerialName("clientHash") val clientHash: String,
        // This is the epoch reached by a committed operation, never the current epoch cache.
        @SerialName("completed") val completed: Map<String, ULong>,
        @SerialName("processed") val processed: Set<String> = emptySet(),
    )

    private companion object {
        const val PAYLOAD = "payload:"
        const val FORMAT = "kalium.mls.receive-recovery.v1"
        val HASH = Regex("[0-9a-f]{64}")
    }
}
