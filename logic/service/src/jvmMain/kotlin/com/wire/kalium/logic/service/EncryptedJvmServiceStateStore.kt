@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)
@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

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

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.CallingEventIdempotencyResult
import com.wire.kalium.calling.runtime.CallingEventIdempotencyStore
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.conversation.ConversationProtocolStateResult
import com.wire.kalium.conversation.ConversationProtocolStateStore
import com.wire.kalium.events.EventAcknowledgement
import com.wire.kalium.events.EventAcknowledgementRecovery
import com.wire.kalium.events.EventCursor
import com.wire.kalium.events.EventDeliveryState
import com.wire.kalium.events.EventDeliveryStateResult
import com.wire.kalium.events.EventDeliveryStateStore
import com.wire.kalium.events.EventIdempotencyKey
import com.wire.kalium.events.PendingEventAcknowledgement
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedId
import com.wire.kalium.network.api.model.SessionDTO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Result returned by the JVM-only encrypted service state views. */
@ExperimentalKaliumServiceApi
public sealed interface EncryptedServiceStateResult<out Value> {
    public data class Success<Value>(public val value: Value) : EncryptedServiceStateResult<Value>

    public data class Failure(public val description: String, public val cause: Throwable? = null) :
        EncryptedServiceStateResult<Nothing>
}

/** Durable credentials for the exact Wire identity bound to the owning encrypted store. */
@ExperimentalKaliumServiceApi
public interface ServiceSessionStore {
    public suspend fun loadSession(): EncryptedServiceStateResult<SessionDTO?>

    public suspend fun saveSession(session: SessionDTO): EncryptedServiceStateResult<Unit>
}

/**
 * Opaque decrypted events retained after crypto mutation and before required handlers complete.
 * Payload interpretation remains the responsibility of the supplied event adapter.
 */
@ExperimentalKaliumServiceApi
public interface DecryptedEventJournal {
    public suspend fun eventKeys(): EncryptedServiceStateResult<Set<String>>

    public suspend fun loadEvent(key: String): EncryptedServiceStateResult<ByteArray?>

    public suspend fun saveEvent(key: String, payload: ByteArray): EncryptedServiceStateResult<Unit>

    public suspend fun removeEvent(key: String): EncryptedServiceStateResult<Unit>
}

/** Opaque encrypted calling signals retained until Wire transport accepts them. */
@ExperimentalKaliumServiceApi
public interface CallingSignalOutbox {
    public suspend fun loadSignals(): EncryptedServiceStateResult<Map<String, ByteArray>>

    public suspend fun putSignal(signalId: String, payload: ByteArray): EncryptedServiceStateResult<Unit>

    public suspend fun removeSignal(signalId: String): EncryptedServiceStateResult<Unit>
}

/** Durable intent around the non-transactional boundary between Kalium and native AVS. */
@ExperimentalKaliumServiceApi
public sealed interface AvsDeliveryReservationResult {
    public data object Acquired : AvsDeliveryReservationResult

    public data object AlreadyAccepted : AvsDeliveryReservationResult

    /** A prior process stopped after reserving delivery; replay requires operator evidence. */
    public data object Ambiguous : AvsDeliveryReservationResult

    public data class Failure(public val description: String, public val cause: Throwable? = null) :
        AvsDeliveryReservationResult
}

@ExperimentalKaliumServiceApi
public interface AvsDeliveryJournal {
    public suspend fun reserveDelivery(key: String): AvsDeliveryReservationResult

    public suspend fun markDeliveryAccepted(key: String): EncryptedServiceStateResult<Unit>

    /** Clears an ambiguous intent only after an operator establishes that AVS did not accept it. */
    public suspend fun releaseDelivery(key: String): EncryptedServiceStateResult<Unit>
}

/** Durable first-fire timers for CoreCrypto pending-proposal commits. */
@ExperimentalKaliumServiceApi
public interface PendingMlsCommitStore {
    public suspend fun loadPendingMlsCommits(): EncryptedServiceStateResult<Map<String, Long>>

    public suspend fun schedulePendingMlsCommit(groupId: String, commitAtEpochSeconds: Long): EncryptedServiceStateResult<Unit>

    public suspend fun removePendingMlsCommit(
        groupId: String,
        expectedCommitAtEpochSeconds: Long,
    ): EncryptedServiceStateResult<Unit>
}

/**
 * Identity-scoped JVM state needed to restart the headless service safely.
 *
 * Every state file is authenticated with AES-GCM and committed using a forced, same-directory
 * atomic move. The supplied [key] is copied on construction and zeroed by [close]. The caller
 * remains responsible for clearing its original key and for keeping key material outside [root].
 * One process-wide exclusive file lock is held for this exact [identity] until [close].
 */
@ExperimentalKaliumServiceApi
@Suppress("LargeClass", "TooManyFunctions")
public class EncryptedJvmServiceStateStore(
    root: Path,
    public val identity: ServiceIdentity,
    key: ByteArray,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : EventDeliveryStateStore,
    CallingEventIdempotencyStore,
    ConversationProtocolStateStore,
    ConferenceProtocolStateStore,
    ServiceSessionStore,
    DecryptedEventJournal,
    CallingSignalOutbox,
    AvsDeliveryJournal,
    PendingMlsCommitStore,
    AutoCloseable {

    private val encryptionKey: ByteArray = key.copyOf().also {
        require(it.size == KEY_SIZE_BYTES) { "key must contain exactly $KEY_SIZE_BYTES bytes" }
    }
    private val identityBytes: ByteArray = encodeIdentity(identity)
    private val identityDirectory: Path = prepareIdentityDirectory(root, identityBytes)
    private val lockHandle: LockHandle = acquireLock(identityDirectory)
    private val random: SecureRandom = SecureRandom()
    private val mutex: Mutex = Mutex()
    private val lifecycleMonitor: Any = Any()

    @Volatile
    private var closed: Boolean = false

    @Volatile
    private var closeStarted: Boolean = false

    @Volatile
    private var terminalFailure: Throwable? = null

    private var lockReleased: Boolean = false
    private var channelClosed: Boolean = false

    private var session: SessionDTO? = null
    private var delivery: DeliverySnapshot = DeliverySnapshot()
    private var callingKeys: LinkedHashSet<String> = linkedSetOf()
    private var pendingAvsDeliveries: LinkedHashSet<String> = linkedSetOf()
    private var pendingMlsCommits: LinkedHashMap<String, Long> = linkedMapOf()
    private var protocols: LinkedHashMap<ConversationId, CallConversationProtocol> = linkedMapOf()
    private var conferences: LinkedHashMap<ConversationId, ConferenceProtocolState> = linkedMapOf()
    private var pendingConferenceCrls: LinkedHashMap<ConversationId, PendingConferenceCrlState> = linkedMapOf()
    private var decryptedEvents: LinkedHashMap<String, ByteArray> = linkedMapOf()
    private var signals: LinkedHashMap<String, ByteArray> = linkedMapOf()

    init {
        try {
            require(maxEntries in 1..MAX_CONFIGURABLE_ENTRIES) {
                "maxEntries must be between 1 and $MAX_CONFIGURABLE_ENTRIES"
            }
            session = readState<SessionDTO?>(StateKind.SESSION, null, ::readSession)
            delivery = readState(StateKind.DELIVERY, DeliverySnapshot(), ::readDelivery)
            callingKeys = readState(StateKind.CALLING_IDEMPOTENCY, linkedSetOf<String>(), ::readStringSet)
            pendingAvsDeliveries = readState(StateKind.AVS_DELIVERY_INTENTS, linkedSetOf<String>(), ::readStringSet)
            pendingMlsCommits = readState(StateKind.PENDING_MLS_COMMITS, linkedMapOf<String, Long>(), ::readLongMap)
            protocols = readState(
                StateKind.CONVERSATION_PROTOCOLS,
                linkedMapOf<ConversationId, CallConversationProtocol>(),
                ::readProtocols,
            )
            conferences = readState(
                StateKind.CONFERENCE_PROTOCOLS,
                linkedMapOf<ConversationId, ConferenceProtocolState>(),
                ::readConferences,
            )
            pendingConferenceCrls = readState(
                StateKind.PENDING_CONFERENCE_CRLS,
                linkedMapOf<ConversationId, PendingConferenceCrlState>(),
                ::readPendingConferenceCrls,
            )
            decryptedEvents = readState(
                StateKind.DECRYPTED_EVENT_JOURNAL,
                linkedMapOf<String, ByteArray>(),
                ::readByteMap,
            )
            signals = readState(StateKind.CALLING_SIGNAL_OUTBOX, linkedMapOf<String, ByteArray>(), ::readByteMap)
        } catch (failure: Throwable) {
            beginClose()
            releaseResources()?.let(failure::addSuppressed)
            throw IllegalStateException("Unable to open encrypted service state", failure)
        }
    }

    override suspend fun loadSession(): EncryptedServiceStateResult<SessionDTO?> = serviceStateOperation(
        description = "Unable to load the durable service session",
    ) {
        session
    }

    override suspend fun saveSession(session: SessionDTO): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to save the durable service session",
    ) {
        requireSessionIdentity(session)
        writeState(StateKind.SESSION) { output -> writeSession(output, session) }
        this.session = session
    }

    override suspend fun loadState(): EventDeliveryStateResult<EventDeliveryState> = eventStateOperation(
        description = "Unable to load durable event delivery state",
    ) {
        EventDeliveryState(
            acknowledgedCursor = delivery.acknowledgedCursor,
            pendingAcknowledgements = delivery.records.mapNotNull { (key, record) ->
                record.acknowledgement
                    ?.takeUnless { record.acknowledged }
                    ?.let { PendingEventAcknowledgement(EventIdempotencyKey(key), it) }
            },
        )
    }

    override suspend fun isHandled(key: EventIdempotencyKey): EventDeliveryStateResult<Boolean> = eventStateOperation(
        description = "Unable to read event idempotency state",
    ) {
        delivery.records.containsKey(key.value)
    }

    override suspend fun recordHandled(
        key: EventIdempotencyKey,
        cursor: EventCursor,
        acknowledgement: EventAcknowledgement?,
        advancesCheckpoint: Boolean,
    ): EventDeliveryStateResult<Unit> = eventStateOperation(
        description = "Unable to record handled event state",
    ) {
        val newRecord = DeliveryRecord(cursor, acknowledgement, advancesCheckpoint, acknowledged = false)
        delivery.records[key.value]?.let { existing ->
            val sameDelivery = existing.cursor == cursor && existing.advancesCheckpoint == advancesCheckpoint
            val sameAcknowledgement = existing.acknowledged || existing.acknowledgement == acknowledgement
            check(sameDelivery && sameAcknowledgement) {
                "Event idempotency key is already associated with different delivery state"
            }
            return@eventStateOperation
        }
        ensureCapacity(delivery.records.size, "event delivery entries")
        val next = delivery.copy(records = LinkedHashMap(delivery.records).apply { put(key.value, newRecord) })
        writeState(StateKind.DELIVERY) { output -> writeDelivery(output, next) }
        delivery = next
    }

    override suspend fun recordAcknowledged(key: EventIdempotencyKey): EventDeliveryStateResult<Unit> = eventStateOperation(
        description = "Unable to record acknowledged event state",
    ) {
        val current = checkNotNull(delivery.records[key.value]) {
            "Cannot acknowledge an event before it is durably handled"
        }
        if (current.acknowledged) return@eventStateOperation

        val acknowledged = current.copy(acknowledgement = null, acknowledged = true)
        val next = delivery.copy(
            acknowledgedCursor = if (current.advancesCheckpoint) current.cursor else delivery.acknowledgedCursor,
            records = LinkedHashMap(delivery.records).apply { put(key.value, acknowledged) },
        )
        writeState(StateKind.DELIVERY) { output -> writeDelivery(output, next) }
        delivery = next
    }

    override suspend fun isHandled(key: String): CallingEventIdempotencyResult = callingOperation(
        description = "Unable to read calling idempotency state",
    ) {
        val accepted = callingKeys.contains(key)
        if (accepted && key in pendingAvsDeliveries) {
            val pending = LinkedHashSet(pendingAvsDeliveries).apply { remove(key) }
            writeState(StateKind.AVS_DELIVERY_INTENTS) { output -> writeStringSet(output, pending) }
            pendingAvsDeliveries = pending
        }
        accepted
    }

    override suspend fun markHandled(key: String): CallingEventIdempotencyResult = callingOperation(
        description = "Unable to record calling idempotency state",
    ) {
        if (callingKeys.contains(key)) return@callingOperation true
        ensureCapacity(callingKeys.size, "calling idempotency entries")
        val next = LinkedHashSet(callingKeys).apply { add(key) }
        writeState(StateKind.CALLING_IDEMPOTENCY) { output -> writeStringSet(output, next) }
        callingKeys = next
        true
    }

    override suspend fun reserveDelivery(key: String): AvsDeliveryReservationResult = try {
        access {
            requireBoundedString(key)
            when {
                key in callingKeys -> {
                    if (key in pendingAvsDeliveries) {
                        val pending = LinkedHashSet(pendingAvsDeliveries).apply { remove(key) }
                        writeState(StateKind.AVS_DELIVERY_INTENTS) { output -> writeStringSet(output, pending) }
                        pendingAvsDeliveries = pending
                    }
                    AvsDeliveryReservationResult.AlreadyAccepted
                }
                key in pendingAvsDeliveries -> AvsDeliveryReservationResult.Ambiguous
                else -> {
                    ensureCapacity(pendingAvsDeliveries.size, "AVS delivery intents")
                    val next = LinkedHashSet(pendingAvsDeliveries).apply { add(key) }
                    writeState(StateKind.AVS_DELIVERY_INTENTS) { output -> writeStringSet(output, next) }
                    pendingAvsDeliveries = next
                    AvsDeliveryReservationResult.Acquired
                }
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        AvsDeliveryReservationResult.Failure("Unable to reserve durable AVS delivery", failure)
    }

    override suspend fun markDeliveryAccepted(key: String): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to persist native AVS delivery acceptance",
    ) {
        if (key !in callingKeys) {
            ensureCapacity(callingKeys.size, "calling idempotency entries")
            val accepted = LinkedHashSet(callingKeys).apply { add(key) }
            writeState(StateKind.CALLING_IDEMPOTENCY) { output -> writeStringSet(output, accepted) }
            callingKeys = accepted
        }
        if (key in pendingAvsDeliveries) {
            val pending = LinkedHashSet(pendingAvsDeliveries).apply { remove(key) }
            writeState(StateKind.AVS_DELIVERY_INTENTS) { output -> writeStringSet(output, pending) }
            pendingAvsDeliveries = pending
        }
    }

    override suspend fun releaseDelivery(key: String): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to release durable AVS delivery intent",
    ) {
        if (key !in pendingAvsDeliveries) return@serviceStateOperation
        val pending = LinkedHashSet(pendingAvsDeliveries).apply { remove(key) }
        writeState(StateKind.AVS_DELIVERY_INTENTS) { output -> writeStringSet(output, pending) }
        pendingAvsDeliveries = pending
    }

    override suspend fun loadPendingMlsCommits(): EncryptedServiceStateResult<Map<String, Long>> = serviceStateOperation(
        description = "Unable to load pending MLS proposal commits",
    ) {
        pendingMlsCommits.toMap()
    }

    override suspend fun schedulePendingMlsCommit(
        groupId: String,
        commitAtEpochSeconds: Long,
    ): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to schedule pending MLS proposal commit",
    ) {
        requireBoundedString(groupId)
        val existing = pendingMlsCommits[groupId]
        if (existing != null && existing <= commitAtEpochSeconds) return@serviceStateOperation
        if (existing == null) ensureCapacity(pendingMlsCommits.size, "pending MLS commit entries")
        val next = LinkedHashMap(pendingMlsCommits).apply { put(groupId, commitAtEpochSeconds) }
        writeState(StateKind.PENDING_MLS_COMMITS) { output -> writeLongMap(output, next) }
        pendingMlsCommits = next
    }

    override suspend fun removePendingMlsCommit(
        groupId: String,
        expectedCommitAtEpochSeconds: Long,
    ): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to remove pending MLS proposal commit",
    ) {
        if (pendingMlsCommits[groupId] != expectedCommitAtEpochSeconds) return@serviceStateOperation
        val next = LinkedHashMap(pendingMlsCommits).apply { remove(groupId) }
        writeState(StateKind.PENDING_MLS_COMMITS) { output -> writeLongMap(output, next) }
        pendingMlsCommits = next
    }

    override suspend fun get(conversationId: ConversationId): ConversationProtocolStateResult<CallConversationProtocol?> =
        conversationStateOperation("Unable to read conversation protocol state") {
            protocols[conversationId]
        }

    override suspend fun save(
        conversationId: ConversationId,
        protocol: CallConversationProtocol,
    ): ConversationProtocolStateResult<Unit> = conversationStateOperation(
        description = "Unable to save conversation protocol state",
    ) {
        if (!protocols.containsKey(conversationId)) ensureCapacity(protocols.size, "conversation protocol entries")
        val next = LinkedHashMap(protocols).apply { put(conversationId, protocol) }
        writeState(StateKind.CONVERSATION_PROTOCOLS) { output -> writeProtocols(output, next) }
        protocols = next
    }

    override suspend fun remove(conversationId: ConversationId): ConversationProtocolStateResult<Unit> = conversationStateOperation(
        description = "Unable to remove conversation protocol state",
    ) {
        if (!protocols.containsKey(conversationId)) return@conversationStateOperation
        val next = LinkedHashMap(protocols).apply { remove(conversationId) }
        writeState(StateKind.CONVERSATION_PROTOCOLS) { output -> writeProtocols(output, next) }
        protocols = next
    }

    override suspend fun loadConference(
        conversationId: ConversationId,
    ): ConferenceProtocolStateResult<ConferenceProtocolState?> = conferenceStateOperation(
        description = "Unable to read conference protocol state",
    ) {
        conferences[conversationId]
    }

    override suspend fun loadConferences(): ConferenceProtocolStateResult<List<ConferenceProtocolState>> = conferenceStateOperation(
        description = "Unable to enumerate conference protocol state",
    ) {
        conferences.values.toList()
    }

    override suspend fun saveConference(state: ConferenceProtocolState): ConferenceProtocolStateResult<Unit> = conferenceStateOperation(
        description = "Unable to save conference protocol state",
    ) {
        if (!conferences.containsKey(state.conversationId)) ensureCapacity(conferences.size, "conference protocol entries")
        val next = LinkedHashMap(conferences).apply { put(state.conversationId, state) }
        writeState(StateKind.CONFERENCE_PROTOCOLS) { output -> writeConferences(output, next) }
        conferences = next
    }

    override suspend fun loadPendingConferenceCrls(): ConferenceProtocolStateResult<List<PendingConferenceCrlState>> =
        conferenceStateOperation(
            description = "Unable to enumerate pending conference CRL state",
        ) {
            pendingConferenceCrls.values.map { it.copy(distributionPoints = it.distributionPoints.toList()) }
        }

    override suspend fun loadPendingConferenceCrl(
        conversationId: ConversationId,
    ): ConferenceProtocolStateResult<PendingConferenceCrlState?> = conferenceStateOperation(
        description = "Unable to read pending conference CRL state",
    ) {
        pendingConferenceCrls[conversationId]?.let { it.copy(distributionPoints = it.distributionPoints.toList()) }
    }

    override suspend fun savePendingConferenceCrl(
        state: PendingConferenceCrlState,
    ): ConferenceProtocolStateResult<Unit> = conferenceStateOperation(
        description = "Unable to save pending conference CRL state",
    ) {
        require(state.distributionPoints.size <= maxEntries) {
            "Pending conference CRL count exceeds configured limit"
        }
        val ownedState = state.copy(distributionPoints = state.distributionPoints.distinct())
        if (!pendingConferenceCrls.containsKey(ownedState.conference.conversationId)) {
            ensureCapacity(pendingConferenceCrls.size, "pending conference CRL entries")
        }
        val next = LinkedHashMap(pendingConferenceCrls).apply {
            put(ownedState.conference.conversationId, ownedState)
        }
        writeState(StateKind.PENDING_CONFERENCE_CRLS) { output -> writePendingConferenceCrls(output, next) }
        pendingConferenceCrls = next
    }

    override suspend fun removePendingConferenceCrl(
        conversationId: ConversationId,
    ): ConferenceProtocolStateResult<Unit> = conferenceStateOperation(
        description = "Unable to remove pending conference CRL state",
    ) {
        if (!pendingConferenceCrls.containsKey(conversationId)) return@conferenceStateOperation
        val next = LinkedHashMap(pendingConferenceCrls).apply { remove(conversationId) }
        writeState(StateKind.PENDING_CONFERENCE_CRLS) { output -> writePendingConferenceCrls(output, next) }
        pendingConferenceCrls = next
    }

    override suspend fun removeConference(conversationId: ConversationId): ConferenceProtocolStateResult<Unit> = conferenceStateOperation(
        description = "Unable to remove conference protocol state",
    ) {
        if (!conferences.containsKey(conversationId)) return@conferenceStateOperation
        val next = LinkedHashMap(conferences).apply { remove(conversationId) }
        writeState(StateKind.CONFERENCE_PROTOCOLS) { output -> writeConferences(output, next) }
        conferences = next
    }

    override suspend fun loadEvent(key: String): EncryptedServiceStateResult<ByteArray?> = serviceStateOperation(
        description = "Unable to load decrypted event journal entry",
    ) {
        decryptedEvents[key]?.copyOf()
    }

    override suspend fun eventKeys(): EncryptedServiceStateResult<Set<String>> = serviceStateOperation(
        description = "Unable to enumerate decrypted event journal entries",
    ) {
        decryptedEvents.keys.toSet()
    }

    override suspend fun saveEvent(key: String, payload: ByteArray): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to save decrypted event journal entry",
    ) {
        saveByteEntry(StateKind.DECRYPTED_EVENT_JOURNAL, decryptedEvents, key, payload, "decrypted event entries") {
            decryptedEvents = it
        }
    }

    override suspend fun removeEvent(key: String): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to remove decrypted event journal entry",
    ) {
        removeByteEntry(StateKind.DECRYPTED_EVENT_JOURNAL, decryptedEvents, key) { decryptedEvents = it }
    }

    override suspend fun loadSignals(): EncryptedServiceStateResult<Map<String, ByteArray>> = serviceStateOperation(
        description = "Unable to load calling signal outbox",
    ) {
        signals.mapValuesTo(linkedMapOf()) { (_, payload) -> payload.copyOf() }
    }

    override suspend fun putSignal(signalId: String, payload: ByteArray): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to save calling signal outbox entry",
    ) {
        saveByteEntry(StateKind.CALLING_SIGNAL_OUTBOX, signals, signalId, payload, "calling signal entries") {
            signals = it
        }
    }

    override suspend fun removeSignal(signalId: String): EncryptedServiceStateResult<Unit> = serviceStateOperation(
        description = "Unable to remove calling signal outbox entry",
    ) {
        removeByteEntry(StateKind.CALLING_SIGNAL_OUTBOX, signals, signalId) { signals = it }
    }

    override fun close(): Unit = synchronized(lifecycleMonitor) {
        if (closed) return
        beginClose()
        val failure = releaseResources()
        if (failure == null) {
            closed = true
            return
        }
        throw IllegalStateException("Unable to release encrypted service state lock", failure)
    }

    private fun beginClose() {
        if (closeStarted) return
        closeStarted = true
        session = null
        delivery.records.clear()
        callingKeys.clear()
        pendingAvsDeliveries.clear()
        pendingMlsCommits.clear()
        protocols.clear()
        conferences.clear()
        pendingConferenceCrls.clear()
        clearByteMap(decryptedEvents)
        clearByteMap(signals)
        encryptionKey.fill(0)
        identityBytes.fill(0)
    }

    private suspend fun <Value> serviceStateOperation(
        description: String,
        block: () -> Value,
    ): EncryptedServiceStateResult<Value> = try {
        EncryptedServiceStateResult.Success(access(block))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        EncryptedServiceStateResult.Failure(description, failure)
    }

    private suspend fun <Value> eventStateOperation(
        description: String,
        block: () -> Value,
    ): EventDeliveryStateResult<Value> = try {
        EventDeliveryStateResult.Success(access(block))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        EventDeliveryStateResult.Failure(description, failure)
    }

    private suspend fun callingOperation(
        description: String,
        block: () -> Boolean,
    ): CallingEventIdempotencyResult = try {
        CallingEventIdempotencyResult.Found(access(block))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        CallingEventIdempotencyResult.Failure(description, failure)
    }

    private suspend fun <Value> conversationStateOperation(
        description: String,
        block: () -> Value,
    ): ConversationProtocolStateResult<Value> = try {
        ConversationProtocolStateResult.Success(access(block))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        ConversationProtocolStateResult.Failure(description, failure)
    }

    private suspend fun <Value> conferenceStateOperation(
        description: String,
        block: () -> Value,
    ): ConferenceProtocolStateResult<Value> = try {
        ConferenceProtocolStateResult.Success(access(block))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        ConferenceProtocolStateResult.Failure(description, failure)
    }

    private suspend fun <Value> access(block: () -> Value): Value = withContext(Dispatchers.IO) {
        mutex.withLock {
            synchronized(lifecycleMonitor) {
                ensureUsable()
                block()
            }
        }
    }

    private fun ensureUsable() {
        check(!closeStarted) { "Encrypted service state store is closing or closed" }
        terminalFailure?.let { throw IllegalStateException("Encrypted service state store failed closed", it) }
    }

    private fun ensureCapacity(currentSize: Int, entryKind: String) {
        check(currentSize < maxEntries) { "Maximum $entryKind limit of $maxEntries reached" }
    }

    private fun requireSessionIdentity(session: SessionDTO) {
        require(session.userId.value == identity.userId.value && session.userId.domain == identity.userId.domain) {
            "Session user identity does not match the encrypted store identity"
        }
    }

    private fun saveByteEntry(
        kind: StateKind,
        current: LinkedHashMap<String, ByteArray>,
        key: String,
        payload: ByteArray,
        entryKind: String,
        update: (LinkedHashMap<String, ByteArray>) -> Unit,
    ) {
        requireBoundedString(key)
        require(payload.size <= MAX_BLOB_BYTES) { "Payload exceeds the $MAX_BLOB_BYTES byte limit" }
        if (!current.containsKey(key)) ensureCapacity(current.size, entryKind)

        val ownedPayload = payload.copyOf()
        val next = LinkedHashMap(current).apply { put(key, ownedPayload) }
        try {
            writeState(kind) { output -> writeByteMap(output, next) }
        } catch (failure: Throwable) {
            ownedPayload.fill(0)
            throw failure
        }
        current[key]?.fill(0)
        update(next)
    }

    private fun removeByteEntry(
        kind: StateKind,
        current: LinkedHashMap<String, ByteArray>,
        key: String,
        update: (LinkedHashMap<String, ByteArray>) -> Unit,
    ) {
        val removed = current[key] ?: return
        val next = LinkedHashMap(current).apply { remove(key) }
        writeState(kind) { output -> writeByteMap(output, next) }
        removed.fill(0)
        update(next)
    }

    private fun clearByteMap(values: MutableMap<String, ByteArray>) {
        values.values.forEach { it.fill(0) }
        values.clear()
    }

    private fun <Value> readState(
        kind: StateKind,
        default: Value,
        decode: (DataInputStream) -> Value,
    ): Value {
        val path = statePath(kind)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return default
        check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "State path is not a regular file: ${kind.fileName}" }
        val fileSize = Files.size(path)
        check(fileSize in 1..MAX_ENCRYPTED_FILE_BYTES.toLong()) { "Encrypted state file has an invalid size" }

        val encrypted = Files.readAllBytes(path)
        val plain = try {
            decrypt(kind, encrypted)
        } finally {
            encrypted.fill(0)
        }
        return try {
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                decode(input).also { check(input.available() == 0) { "State file contains trailing data" } }
            }
        } finally {
            plain.fill(0)
        }
    }

    private fun writeState(kind: StateKind, encode: (DataOutputStream) -> Unit) {
        val plainOutput = LimitedByteArrayOutputStream(MAX_PLAIN_FILE_BYTES)
        val plain = try {
            DataOutputStream(plainOutput).use { output -> encode(output) }
            plainOutput.toByteArray()
        } catch (failure: Throwable) {
            plainOutput.resetAndClear()
            throw failure
        }
        plainOutput.resetAndClear()

        val encrypted = try {
            encrypt(kind, plain)
        } finally {
            plain.fill(0)
        }
        try {
            atomicWrite(statePath(kind), encrypted)
        } catch (failure: Throwable) {
            terminalFailure = failure
            throw failure
        } finally {
            encrypted.fill(0)
        }
    }

    private fun encrypt(kind: StateKind, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptionKey, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, nonce))
            updateAAD(aad(kind))
        }
        val ciphertext = cipher.doFinal(plain)
        val buffer = LimitedByteArrayOutputStream(MAX_ENCRYPTED_FILE_BYTES)
        return try {
            DataOutputStream(buffer).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeInt(FILE_FORMAT_VERSION)
                output.writeByte(kind.id)
                output.writeByte(nonce.size)
                output.write(nonce)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            buffer.toByteArray()
        } finally {
            buffer.resetAndClear()
            ciphertext.fill(0)
            nonce.fill(0)
        }
    }

    private fun decrypt(kind: StateKind, encrypted: ByteArray): ByteArray =
        DataInputStream(ByteArrayInputStream(encrypted)).use { input ->
            check(input.readInt() == FILE_MAGIC) { "Invalid encrypted state magic" }
            check(input.readInt() == FILE_FORMAT_VERSION) { "Unsupported encrypted state version" }
            check(input.readUnsignedByte() == kind.id) { "Encrypted state kind mismatch" }
            val nonceSize = input.readUnsignedByte()
            check(nonceSize == NONCE_SIZE_BYTES) { "Invalid encrypted state nonce" }
            val nonce = ByteArray(nonceSize).also { input.readFully(it) }
            val ciphertextSize = input.readInt()
            check(ciphertextSize in GCM_TAG_BYTES..MAX_ENCRYPTED_FILE_BYTES) { "Invalid encrypted payload size" }
            val ciphertext = ByteArray(ciphertextSize).also { input.readFully(it) }
            check(input.available() == 0) { "Encrypted state file contains trailing data" }
            try {
                Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKey, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, nonce))
                    updateAAD(aad(kind))
                    doFinal(ciphertext)
                }
            } finally {
                nonce.fill(0)
                ciphertext.fill(0)
            }
        }

    private fun aad(kind: StateKind): ByteArray = ByteArrayOutputStream().let { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(FILE_FORMAT_VERSION)
            output.writeByte(kind.id)
            output.writeInt(identityBytes.size)
            output.write(identityBytes)
        }
        buffer.toByteArray()
    }

    private fun atomicWrite(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(identityDirectory, ".${target.fileName}.", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw IOException("Atomic state replacement is not supported by the configured filesystem", unsupported)
            }
            FileChannel.open(identityDirectory, StandardOpenOption.READ).use { directory -> directory.force(true) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun statePath(kind: StateKind): Path = identityDirectory.resolve(kind.fileName)

    private fun releaseResources(): Throwable? {
        var failure: Throwable? = null
        if (!lockReleased) {
            try {
                if (lockHandle.lock.isValid) lockHandle.lock.release()
            } catch (releaseFailure: Throwable) {
                failure = releaseFailure
            } finally {
                lockReleased = !lockHandle.lock.isValid
            }
        }

        if (!channelClosed) {
            try {
                if (lockHandle.channel.isOpen) lockHandle.channel.close()
            } catch (closeFailure: Throwable) {
                if (failure == null) failure = closeFailure else failure.addSuppressed(closeFailure)
            } finally {
                channelClosed = !lockHandle.channel.isOpen
                if (!lockHandle.lock.isValid) lockReleased = true
            }
        }

        if (lockReleased && channelClosed) return null
        return failure ?: IllegalStateException("Encrypted service state lock or channel remains open")
    }

    private fun readSession(input: DataInputStream): SessionDTO {
        val session = SessionDTO(
            userId = NetworkQualifiedId(input.readBoundedString(), input.readBoundedString()),
            tokenType = input.readBoundedString(),
            accessToken = input.readBoundedString(),
            refreshToken = input.readBoundedString(),
            cookieLabel = input.readNullableString(),
        )
        requireSessionIdentity(session)
        return session
    }

    private fun writeSession(output: DataOutputStream, session: SessionDTO) {
        output.writeBoundedString(session.userId.value)
        output.writeBoundedString(session.userId.domain)
        output.writeBoundedString(session.tokenType)
        output.writeBoundedString(session.accessToken)
        output.writeBoundedString(session.refreshToken)
        output.writeNullableString(session.cookieLabel)
    }

    private fun readDelivery(input: DataInputStream): DeliverySnapshot {
        val acknowledgedCursor = input.readNullableString()?.let(::EventCursor)
        val records = linkedMapOf<String, DeliveryRecord>()
        repeat(input.readBoundedCount()) {
            val key = input.readBoundedString()
            check(!records.containsKey(key)) { "Duplicate event delivery key" }
            records[key] = DeliveryRecord(
                cursor = EventCursor(input.readBoundedString()),
                acknowledgement = if (input.readBoolean()) {
                    EventAcknowledgement(
                        value = input.readBoundedString(),
                        recovery = input.readAcknowledgementRecovery(),
                    )
                } else {
                    null
                },
                advancesCheckpoint = input.readBoolean(),
                acknowledged = input.readBoolean(),
            )
        }
        return DeliverySnapshot(acknowledgedCursor, records)
    }

    private fun writeDelivery(output: DataOutputStream, state: DeliverySnapshot) {
        output.writeNullableString(state.acknowledgedCursor?.value)
        output.writeInt(state.records.size)
        state.records.forEach { (key, record) ->
            output.writeBoundedString(key)
            output.writeBoundedString(record.cursor.value)
            output.writeBoolean(record.acknowledgement != null)
            record.acknowledgement?.let { acknowledgement ->
                output.writeBoundedString(acknowledgement.value)
                output.writeAcknowledgementRecovery(acknowledgement.recovery)
            }
            output.writeBoolean(record.advancesCheckpoint)
            output.writeBoolean(record.acknowledged)
        }
    }

    private fun readStringSet(input: DataInputStream): LinkedHashSet<String> = linkedSetOf<String>().apply {
        repeat(input.readBoundedCount()) { check(add(input.readBoundedString())) { "Duplicate stored key" } }
    }

    private fun writeStringSet(output: DataOutputStream, values: Set<String>) {
        output.writeInt(values.size)
        values.forEach { value -> output.writeBoundedString(value) }
    }

    private fun readLongMap(input: DataInputStream): LinkedHashMap<String, Long> = linkedMapOf<String, Long>().apply {
        repeat(input.readBoundedCount()) {
            val key = input.readBoundedString()
            check(!containsKey(key)) { "Duplicate stored timer key" }
            put(key, input.readLong())
        }
    }

    private fun writeLongMap(output: DataOutputStream, values: Map<String, Long>) {
        output.writeInt(values.size)
        values.forEach { (key, value) ->
            output.writeBoundedString(key)
            output.writeLong(value)
        }
    }

    private fun readProtocols(input: DataInputStream): LinkedHashMap<ConversationId, CallConversationProtocol> =
        linkedMapOf<ConversationId, CallConversationProtocol>().apply {
            repeat(input.readBoundedCount()) {
                val conversationId = QualifiedID(input.readBoundedString(), input.readBoundedString())
                check(!containsKey(conversationId)) { "Duplicate conversation protocol key" }
                put(conversationId, input.readProtocol())
            }
        }

    private fun writeProtocols(output: DataOutputStream, values: Map<ConversationId, CallConversationProtocol>) {
        output.writeInt(values.size)
        values.forEach { (conversationId, protocol) ->
            output.writeBoundedString(conversationId.value)
            output.writeBoundedString(conversationId.domain)
            output.writeProtocol(protocol)
        }
    }

    private fun readConferences(input: DataInputStream): LinkedHashMap<ConversationId, ConferenceProtocolState> =
        linkedMapOf<ConversationId, ConferenceProtocolState>().apply {
            repeat(input.readBoundedCount()) {
                val conversationId = QualifiedID(input.readBoundedString(), input.readBoundedString())
                check(!containsKey(conversationId)) { "Duplicate conference protocol key" }
                put(
                    conversationId,
                    ConferenceProtocolState(
                        conversationId = conversationId,
                        parentGroupId = input.readBoundedString(),
                        subgroupId = input.readBoundedString(),
                    ),
                )
            }
        }

    private fun writeConferences(output: DataOutputStream, values: Map<ConversationId, ConferenceProtocolState>) {
        output.writeInt(values.size)
        values.forEach { (conversationId, state) ->
            output.writeBoundedString(conversationId.value)
            output.writeBoundedString(conversationId.domain)
            output.writeBoundedString(state.parentGroupId)
            output.writeBoundedString(state.subgroupId)
        }
    }

    private fun readPendingConferenceCrls(
        input: DataInputStream,
    ): LinkedHashMap<ConversationId, PendingConferenceCrlState> =
        linkedMapOf<ConversationId, PendingConferenceCrlState>().apply {
            repeat(input.readBoundedCount()) {
                val conversation = ConferenceProtocolState(
                    conversationId = QualifiedID(input.readBoundedString(), input.readBoundedString()),
                    parentGroupId = input.readBoundedString(),
                    subgroupId = input.readBoundedString(),
                )
                check(!containsKey(conversation.conversationId)) { "Duplicate pending conference CRL key" }
                val distributionPoints = List(input.readBoundedCount()) { input.readBoundedString() }
                put(conversation.conversationId, PendingConferenceCrlState(conversation, distributionPoints))
            }
        }

    private fun writePendingConferenceCrls(
        output: DataOutputStream,
        values: Map<ConversationId, PendingConferenceCrlState>,
    ) {
        output.writeInt(values.size)
        values.forEach { (conversationId, state) ->
            output.writeBoundedString(conversationId.value)
            output.writeBoundedString(conversationId.domain)
            output.writeBoundedString(state.conference.parentGroupId)
            output.writeBoundedString(state.conference.subgroupId)
            output.writeInt(state.distributionPoints.size)
            state.distributionPoints.forEach { output.writeBoundedString(it) }
        }
    }

    private fun readByteMap(input: DataInputStream): LinkedHashMap<String, ByteArray> {
        val values = linkedMapOf<String, ByteArray>()
        return try {
            repeat(input.readBoundedCount()) {
                val key = input.readBoundedString()
                check(!values.containsKey(key)) { "Duplicate opaque state key" }
                values[key] = input.readBoundedBytes()
            }
            values
        } catch (failure: Throwable) {
            clearByteMap(values)
            throw failure
        }
    }

    private fun writeByteMap(output: DataOutputStream, values: Map<String, ByteArray>) {
        output.writeInt(values.size)
        values.forEach { (key, value) ->
            output.writeBoundedString(key)
            output.writeBoundedBytes(value)
        }
    }

    private fun DataInputStream.readProtocol(): CallConversationProtocol = when (readUnsignedByte()) {
        PROTOCOL_PROTEUS -> CallConversationProtocol.Proteus
        PROTOCOL_MLS -> CallConversationProtocol.Mls(GroupID(readBoundedString()), readNullableEpoch())
        PROTOCOL_MIXED -> CallConversationProtocol.Mixed(GroupID(readBoundedString()), readNullableEpoch())
        else -> error("Unknown conversation protocol tag")
    }

    private fun DataOutputStream.writeProtocol(protocol: CallConversationProtocol) {
        when (protocol) {
            CallConversationProtocol.Proteus -> writeByte(PROTOCOL_PROTEUS)
            is CallConversationProtocol.Mls -> {
                writeByte(PROTOCOL_MLS)
                writeBoundedString(protocol.groupId.value)
                writeNullableEpoch(protocol.epoch)
            }
            is CallConversationProtocol.Mixed -> {
                writeByte(PROTOCOL_MIXED)
                writeBoundedString(protocol.groupId.value)
                writeNullableEpoch(protocol.epoch)
            }
        }
    }

    private fun DataInputStream.readNullableEpoch(): ULong? = if (readBoolean()) readLong().toULong() else null

    private fun DataOutputStream.writeNullableEpoch(epoch: ULong?) {
        writeBoolean(epoch != null)
        epoch?.let { writeLong(it.toLong()) }
    }

    private fun DataInputStream.readAcknowledgementRecovery(): EventAcknowledgementRecovery = when (readUnsignedByte()) {
        ACK_RECOVERY_REPLAY -> EventAcknowledgementRecovery.REPLAY
        ACK_RECOVERY_WAIT_FOR_REDELIVERY -> EventAcknowledgementRecovery.WAIT_FOR_REDELIVERY
        else -> error("Unknown event acknowledgement recovery tag")
    }

    private fun DataOutputStream.writeAcknowledgementRecovery(recovery: EventAcknowledgementRecovery) {
        writeByte(
            when (recovery) {
                EventAcknowledgementRecovery.REPLAY -> ACK_RECOVERY_REPLAY
                EventAcknowledgementRecovery.WAIT_FOR_REDELIVERY -> ACK_RECOVERY_WAIT_FOR_REDELIVERY
            },
        )
    }

    private fun DataInputStream.readBoundedCount(): Int = readInt().also { count ->
        check(count in 0..maxEntries) { "Stored entry count exceeds configured limit" }
    }

    private fun DataInputStream.readBoundedString(): String {
        val size = readInt()
        check(size in 0..MAX_STRING_BYTES) { "Stored string length exceeds configured limit" }
        val bytes = ByteArray(size).also { readFully(it) }
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeBoundedString(value: String) {
        val bytes = value.encodeToByteArray()
        try {
            require(bytes.size <= MAX_STRING_BYTES) { "String exceeds the $MAX_STRING_BYTES byte limit" }
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readBoundedString() else null

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        value?.let { writeBoundedString(it) }
    }

    private fun DataInputStream.readBoundedBytes(): ByteArray {
        val size = readInt()
        check(size in 0..MAX_BLOB_BYTES) { "Stored payload length exceeds configured limit" }
        return ByteArray(size).also { readFully(it) }
    }

    private fun DataOutputStream.writeBoundedBytes(value: ByteArray) {
        require(value.size <= MAX_BLOB_BYTES) { "Payload exceeds the $MAX_BLOB_BYTES byte limit" }
        writeInt(value.size)
        write(value)
    }

    private fun requireBoundedString(value: String) {
        val bytes = value.encodeToByteArray()
        try {
            require(bytes.size <= MAX_STRING_BYTES) { "Key exceeds the $MAX_STRING_BYTES byte limit" }
        } finally {
            bytes.fill(0)
        }
    }

    private data class DeliverySnapshot(
        val acknowledgedCursor: EventCursor? = null,
        val records: LinkedHashMap<String, DeliveryRecord> = linkedMapOf(),
    )

    private data class DeliveryRecord(
        val cursor: EventCursor,
        val acknowledgement: EventAcknowledgement?,
        val advancesCheckpoint: Boolean,
        val acknowledged: Boolean,
    )

    private data class LockHandle(val channel: FileChannel, val lock: FileLock)

    private enum class StateKind(val id: Int, val fileName: String) {
        SESSION(1, "session.state"),
        DELIVERY(2, "delivery.state"),
        CALLING_IDEMPOTENCY(3, "calling-idempotency.state"),
        CONVERSATION_PROTOCOLS(4, "conversation-protocols.state"),
        DECRYPTED_EVENT_JOURNAL(5, "decrypted-events.state"),
        CALLING_SIGNAL_OUTBOX(6, "calling-signals.state"),
        CONFERENCE_PROTOCOLS(7, "conference-protocols.state"),
        PENDING_CONFERENCE_CRLS(8, "pending-conference-crls.state"),
        AVS_DELIVERY_INTENTS(9, "avs-delivery-intents.state"),
        PENDING_MLS_COMMITS(10, "pending-mls-commits.state"),
    }

    private class LimitedByteArrayOutputStream(private val limit: Int) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            ensureRemaining(1)
            super.write(value)
        }

        override fun write(value: ByteArray, offset: Int, length: Int) {
            ensureRemaining(length)
            super.write(value, offset, length)
        }

        fun resetAndClear() {
            buf.fill(0, 0, count)
            reset()
        }

        private fun ensureRemaining(additional: Int) {
            check(additional >= 0 && count <= limit - additional) { "Encoded state exceeds the $limit byte limit" }
        }
    }

    public companion object {
        public const val DEFAULT_MAX_ENTRIES: Int = 100_000

        private const val MAX_CONFIGURABLE_ENTRIES = 1_000_000
        private const val MAX_STRING_BYTES = 1_048_576
        private const val MAX_BLOB_BYTES = 8_388_608
        private const val MAX_PLAIN_FILE_BYTES = 67_108_864
        private const val MAX_ENCRYPTED_FILE_BYTES = MAX_PLAIN_FILE_BYTES + 1_024
        private const val FILE_MAGIC = 0x4B535346
        private const val FILE_FORMAT_VERSION = 1
        private const val KEY_SIZE_BYTES = 32
        private const val NONCE_SIZE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val PROTOCOL_PROTEUS = 1
        private const val PROTOCOL_MLS = 2
        private const val PROTOCOL_MIXED = 3
        private const val ACK_RECOVERY_REPLAY = 1
        private const val ACK_RECOVERY_WAIT_FOR_REDELIVERY = 2

        private fun encodeIdentity(identity: ServiceIdentity): ByteArray = ByteArrayOutputStream().let { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeIdentityString(identity.userId.value)
                output.writeIdentityString(identity.userId.domain)
                output.writeIdentityString(identity.clientId)
                output.writeIdentityString(identity.backendDomain)
            }
            buffer.toByteArray()
        }

        private fun DataOutputStream.writeIdentityString(value: String) {
            val bytes = value.encodeToByteArray()
            try {
                require(bytes.size <= MAX_STRING_BYTES) { "Identity value exceeds the $MAX_STRING_BYTES byte limit" }
                writeInt(bytes.size)
                write(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        private fun prepareIdentityDirectory(root: Path, identityBytes: ByteArray): Path {
            Files.createDirectories(root)
            val canonicalRoot = root.toRealPath()
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(identityBytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
            val candidate = canonicalRoot.resolve(fingerprint)
            Files.createDirectories(candidate)
            val canonicalCandidate = candidate.toRealPath()
            check(canonicalCandidate.parent == canonicalRoot) { "Identity state directory escapes the configured root" }
            return canonicalCandidate
        }

        private fun acquireLock(directory: Path): LockHandle {
            val lockPath = directory.resolve("service.lock")
            check(!Files.isSymbolicLink(lockPath)) { "Service state lock must not be a symbolic link" }
            val channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS,
            )
            try {
                val lock = try {
                    channel.tryLock()
                } catch (overlap: OverlappingFileLockException) {
                    null
                }
                return LockHandle(channel, checkNotNull(lock) { "Service state is already open for this identity" })
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }
    }
}
