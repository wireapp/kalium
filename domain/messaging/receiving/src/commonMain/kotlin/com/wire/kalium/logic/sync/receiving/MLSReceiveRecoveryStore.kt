/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.event.NotificationEventStore
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
 * Owned by one exclusively leased Apple account/client. Reads operation identities once
 * per owner and after an uncertain SQL write; ordinary messages need no per-event query.
 * Callers must write intent before POST and acknowledge only after native commit and effects.
 * Records protect retries of the same durable event, not ciphertext delivered under a new
 * event identity after the original has been acknowledged.
 */
class MLSReceiveRecoveryStore(
    private val metadata: MetadataDAO,
    private val events: NotificationEventStore,
    clientId: String,
) {
    private val clientHash = clientId.encodeUtf8().sha256().hex()
    private val prefix = "mls_receive_recovery_v2:$clientHash:"
    private val until = "mls_receive_recovery_v2:$clientHash;"
    private val mutex = Mutex()
    private var pending: Map<String, PendingMLSReceiveRecovery>? = null

    init {
        require(clientId.isNotBlank())
    }

    /** The payload is decoded/hashed only when this event has an outstanding operation. */
    suspend fun find(eventId: String, ciphertext: () -> ByteArray): PendingMLSReceiveRecovery? = mutex.withLock {
        val candidates = load().values.filter { it.eventId == eventId }
        if (candidates.isEmpty()) return@withLock null
        val hash = ciphertext().toByteString().sha256().hex()
        candidates.firstOrNull { it.ciphertextHash == hash }
    }

    suspend fun begin(eventId: String, groupId: String, ciphertext: ByteArray): PendingMLSReceiveRecovery = mutex.withLock {
        val entries = load()
        val operation = operation(eventId, groupId, ciphertext)
        check(
            entries.values.none {
                it.eventId == eventId && it.ciphertextHash == operation.ciphertextHash && it.groupId != groupId
            }
        ) { "Recovery operation belongs to a different MLS group" }
        val key = prefix + operation.id
        if (entries[key] != null) return@withLock entries.getValue(key)
        uncertainWrite {
            metadata.insertValue(KtxSerializer.json.encodeToString(operation), key)
            pending = entries + (key to operation)
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
        val outstanding = entries.values.map { it.id }.toSet()
        val joinedEpoch = context.conversationEpoch(operation.groupId)
        check(joinedEpoch > 0uL) { "An external join must advance the group epoch" }
        val completed = checkpoint.completed.filterKeys { it in outstanding } + (operation.id to joinedEpoch)
        context.writeTransactionCheckpoint(KtxSerializer.json.encodeToString(checkpoint.copy(completed = completed)).encodeToByteArray())
    }

    /** SQL retirement and queue markers commit together; no native completion is inferred from an epoch. */
    suspend fun acknowledge(eventIds: List<String>) = mutex.withLock {
        val entries = load()
        val ids = eventIds.toSet()
        val retiring = entries.filterValues { it.eventId in ids }
        val keysByEvent = retiring.entries.groupBy({ it.value.eventId }, { it.key })
        uncertainWrite {
            events.markProcessed(eventIds, keysByEvent)
            pending = entries - retiring.keys
        }
    }

    private suspend fun load(): Map<String, PendingMLSReceiveRecovery> {
        pending?.let { return it }
        return metadata.valuesByKeyRange(prefix, until).mapValues { (key, value) ->
            KtxSerializer.json.decodeFromString<PendingMLSReceiveRecovery>(value).also {
                check(it.eventId.isNotBlank() && it.groupId.isNotBlank() && HASH.matches(it.ciphertextHash))
                check(key == prefix + it.id) { "Recovery operation identity does not match its key" }
            }
        }.also { pending = it }
    }

    private suspend fun requirePending(operation: PendingMLSReceiveRecovery): Map<String, PendingMLSReceiveRecovery> = load().also {
        check(it[prefix + operation.id] == operation) { "Recovery operation is not pending for this client" }
    }

    private suspend fun readCheckpoint(context: MlsCoreCryptoContext): RecoveryCheckpoint {
        val bytes = context.readTransactionCheckpoint() ?: return RecoveryCheckpoint(FORMAT, clientHash, emptyMap())
        return KtxSerializer.json.decodeFromString<RecoveryCheckpoint>(bytes.decodeToString()).also {
            check(it.format == FORMAT && it.clientHash == clientHash) { "Unsupported native receive checkpoint" }
            check(it.completed.keys.all(HASH::matches)) { "Invalid native receive checkpoint identity" }
            check(it.completed.values.all { epoch -> epoch > 0uL }) { "Invalid native completion epoch" }
        }
    }

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

    @Serializable
    private data class RecoveryCheckpoint(
        @SerialName("format") val format: String,
        @SerialName("clientHash") val clientHash: String,
        // This is the epoch reached by a committed operation, never the current epoch cache.
        @SerialName("completed") val completed: Map<String, ULong>,
    )

    private companion object {
        const val FORMAT = "kalium.mls.receive-recovery.v1"
        val HASH = Regex("[0-9a-f]{64}")
    }
}
