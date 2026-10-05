/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Encrypted account storage. App and NSE must hold the existing exclusive account lease. */
@Serializable
data class CallSignalRecord(
    @SerialName("id") val id: String,
    @SerialName("messageId") val messageId: String,
    @SerialName("conversationValue") val conversationValue: String,
    @SerialName("conversationDomain") val conversationDomain: String,
    @SerialName("senderValue") val senderValue: String,
    @SerialName("senderDomain") val senderDomain: String,
    @SerialName("senderClientId") val senderClientId: String,
    @SerialName("payload") val payload: String,
    @SerialName("timeMillis") val timeMillis: Long,
    @SerialName("isGroup") val isGroup: Boolean,
    @SerialName("isMLS") val isMLS: Boolean,
    @SerialName("storedAtMillis") val storedAtMillis: Long,
    @SerialName("submittedGeneration") val submittedGeneration: String? = null,
    @SerialName("incomingGeneration") val incomingGeneration: String? = null,
    @SerialName("ready") val ready: Boolean = false,
)

@Serializable
data class CallPresentationRecord(
    @SerialName("id") val id: String,
    @SerialName("conversationValue") val conversationValue: String,
    @SerialName("conversationDomain") val conversationDomain: String,
    @SerialName("startTimeMillis") val startTimeMillis: Long,
    @SerialName("ringing") val ringing: Boolean,
    @SerialName("hasVideo") val hasVideo: Boolean,
    @SerialName("endReason") val endReason: Int? = null,
)

@Serializable
internal data class CallSignalJournalState(
    @SerialName("signals") val signals: List<CallSignalRecord> = emptyList(),
    @SerialName("presentations") val presentations: List<CallPresentationRecord> = emptyList(),
)

/** Native callback evidence; equal references can describe more than one stored start request. */
data class IncomingCallReference(
    val conversation: QualifiedIDEntity,
    val caller: QualifiedIDEntity,
    val callerClientId: String?,
    val messageTimeSeconds: Long,
)

/** Durable signaling and presentation decisions, independently of the native handle lifetime. */
interface CallSignalStore {
    suspend fun signals(): List<CallSignalRecord>
    suspend fun presentations(): List<CallPresentationRecord>
    suspend fun append(event: ReceivedCallEvent): CallSignalRecord
    suspend fun submitted(id: String, generation: String)

    suspend fun incoming(
        reference: IncomingCallReference,
        ring: Boolean,
        video: Boolean,
        generation: String
    ): CallPresentationRecord?
    suspend fun closed(conversationValue: String, conversationDomain: String, reason: Int): String?
}

/**
 * Submission is an acknowledgement of native enqueue, not an exactly-once AVS effect.
 * Retain accepted signals for replay into a NEW native handle after a host crash.
 * A live handle uses one generation token to suppress re-enqueue after duplicate pushes.
 */
class CallSignalJournal(private val database: UserDatabaseBuilder) : CallSignalStore {
    override suspend fun signals(): List<CallSignalRecord> = read(database.metadataDAO).signals.filter { it.ready }
    override suspend fun presentations(): List<CallPresentationRecord> = read(database.metadataDAO).presentations
    fun observePresentations() = database.metadataDAO.observeSerializable(KEY, CallSignalJournalState.serializer())
        .map { it?.presentations.orEmpty() }.distinctUntilChanged()

    /** Read-only NSE lookup. The full engine alone changes ringing and termination state. */
    suspend fun incomingSource(reference: IncomingCallReference): CallSignalRecord? =
        read(database.metadataDAO).incomingSource(reference)

    override suspend fun append(event: ReceivedCallEvent): CallSignalRecord = database.writeMessageEffects {
        val record = append(metadata, event)
        val state = read(metadata)
        write(metadata, state.copy(signals = state.signals.map { if (it.id == record.id) it.copy(ready = true) else it }))
        record.copy(ready = true)
    }

    suspend fun committed(events: List<ReceivedCallEvent>) {
        if (events.isEmpty()) return
        database.writeMessageEffects {
            val ids = events.map { append(metadata, it).id }.toSet()
            val state = read(metadata)
            write(metadata, state.copy(signals = state.signals.map { if (it.id in ids) it.copy(ready = true) else it }))
        }
    }

    override suspend fun submitted(id: String, generation: String) = database.writeMessageEffects {
        val state = read(metadata)
        write(
            metadata,
            state.copy(
                signals = state.signals.map {
            if (it.id == id) it.copy(submittedGeneration = generation) else it
        }
            )
        )
    }

    override suspend fun incoming(
        reference: IncomingCallReference,
        ring: Boolean,
        video: Boolean,
        generation: String,
    ): CallPresentationRecord? = database.writeMessageEffects {
        val state = read(metadata)
        val source = state.incomingSource(reference) ?: return@writeMessageEffects null
        val existing = state.presentations.find { it.id == source.id }
        val presentation = when {
            !ring && existing != null -> existing.copy(ringing = false)
            existing != null -> existing // A replay cannot resurrect a completed presentation.
            else -> CallPresentationRecord(
                source.id,
                source.conversationValue,
                source.conversationDomain,
                source.timeMillis,
                ring,
                video,
            )
        }
        // Native close callbacks carry only the conversation. Keep incoming callback order,
        // including replay, so closing an old replay cannot end a newer stored presentation.
        write(
            metadata,
            state.copy(
                signals = state.signals.map { if (it.id == source.id) it.copy(incomingGeneration = generation) else it },
                presentations = state.presentations.filterNot { it.id == presentation.id } + presentation,
            )
        )
        presentation
    }

    /**
     * Both AVS adapters use the first ready start matching the callback evidence. Generations
     * acknowledge native work, not a cursor through tied starts. AVS exposes neither message ID
     * nor sessid, so identical references cannot distinguish separate real calls.
     */
    private fun CallSignalJournalState.incomingSource(reference: IncomingCallReference): CallSignalRecord? =
        signals.firstOrNull {
            it.ready && it.conversationValue == reference.conversation.value && it.conversationDomain == reference.conversation.domain &&
                it.senderValue == reference.caller.value && it.senderDomain == reference.caller.domain &&
                (reference.callerClientId == null || it.senderClientId == reference.callerClientId) &&
                it.timeMillis / MILLISECONDS_PER_SECOND == reference.messageTimeSeconds && it.isStartRequest()
        }

    override suspend fun closed(
        conversationValue: String,
        conversationDomain: String,
        reason: Int,
    ): String? = database.writeMessageEffects {
        val state = read(metadata)
        val current = state.presentations.lastOrNull {
            it.conversationValue == conversationValue && it.conversationDomain == conversationDomain
        } ?: return@writeMessageEffects null
        write(
            metadata,
            state.copy(
                presentations = state.presentations.map {
            if (it.id == current.id) it.copy(ringing = false, endReason = reason) else it
        }
            )
        )
        current.id
    }

    private fun CallSignalRecord.isStartRequest(): Boolean = runCatching {
        val json = Json.parseToJsonElement(payload).jsonObject
        json["type"]?.jsonPrimitive?.content?.uppercase() in setOf("SETUP", "GROUPSTART", "CONFSTART") &&
            json["resp"]?.jsonPrimitive?.booleanOrNull == false
    }.getOrDefault(false)

    companion object {
        internal const val MAX_SIGNALS = 4096
        internal const val KEY = "apple_call_signal_journal_v1"
        private const val MILLISECONDS_PER_SECOND = 1000L
        private const val RETENTION_MS = 24 * 60 * 60 * MILLISECONDS_PER_SECOND

        /** Called inside the SAME SQL transaction as receiving effects, before the processed marker. */
        suspend fun append(metadata: MetadataDAO, event: ReceivedCallEvent): CallSignalRecord {
            require(event.messageId.isNotBlank())
            val state = read(metadata)
            state.signals.firstOrNull {
                it.messageId == event.messageId && it.conversationValue == event.conversationId.value &&
                    it.conversationDomain == event.conversationId.domain && it.senderValue == event.senderId.value &&
                    it.senderDomain == event.senderId.domain && it.senderClientId == event.senderClientId
            }?.let {
                check(it.payload == event.payload) { "Call signal identity conflict" }
                return it
            }
            val now = Clock.System.now().toEpochMilliseconds()
            val activeConversations = state.presentations.filter { it.ringing && it.endReason == null }
                .map { it.conversationValue to it.conversationDomain }.toSet()
            val retained = state.signals.filter {
                it.submittedGeneration == null || now - it.storedAtMillis < RETENTION_MS ||
                    (it.conversationValue to it.conversationDomain) in activeConversations
            }
            // A full journal must not block receiving, so drop the oldest signals instead.
            val dropped = (retained.size - MAX_SIGNALS + 1).coerceAtLeast(0)
            if (dropped > 0) kaliumLogger.w("Call signal journal is full; dropping $dropped oldest signals")
            val record = CallSignalRecord(
                Uuid.random().toString(), event.messageId, event.conversationId.value, event.conversationId.domain,
                event.senderId.value, event.senderId.domain, event.senderClientId, event.payload,
                event.time.toEpochMilliseconds(), event.isGroup, event.isMLS, now,
            )
            write(
                metadata,
                state.copy(
                    signals = retained.drop(dropped) + record,
                presentations = state.presentations.filter { it.ringing || now - it.startTimeMillis < RETENTION_MS }
                )
            )
            return record
        }

        private suspend fun read(metadata: MetadataDAO): CallSignalJournalState =
            metadata.getSerializable(KEY, CallSignalJournalState.serializer()) ?: CallSignalJournalState()

        private suspend fun write(metadata: MetadataDAO, state: CallSignalJournalState) =
            metadata.putSerializable(KEY, state, CallSignalJournalState.serializer())
    }
}
