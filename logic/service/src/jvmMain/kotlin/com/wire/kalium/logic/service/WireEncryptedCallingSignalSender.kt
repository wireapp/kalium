@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)
@file:Suppress("TooGenericExceptionCaught")

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
 */

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.CallSignalTarget
import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.OutgoingCallingSignal
import com.wire.kalium.calling.runtime.SelfConversationResult
import com.wire.kalium.calling.runtime.SelfConversationTarget
import com.wire.kalium.calling.WireCallingMessageCodec
import com.wire.kalium.calling.runtime.ServiceSelfConversationProvider
import com.wire.kalium.conversation.CallClient
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.network.api.authenticated.message.MessagePriority
import com.wire.kalium.network.api.authenticated.message.Parameters
import com.wire.kalium.network.api.authenticated.message.QualifiedMessageOption
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedId
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.protobuf.encodeToByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicit, durable self-conversation targets; an empty implicit fallback is never installed. */
@ExperimentalKaliumServiceApi
public class ConfiguredServiceSelfConversationProvider(targets: List<SelfConversationTarget>) : ServiceSelfConversationProvider {
    private val configuredTargets: List<SelfConversationTarget> = targets.distinctBy { it.conversationId }

    init {
        require(configuredTargets.isNotEmpty()) { "At least one durable service self-conversation target is required" }
    }

    override suspend fun getSelfConversations(): SelfConversationResult = SelfConversationResult.Success(configuredTargets)
}

/**
 * Direct calling-only sender. It uses no message history, message DAO, full-sync recovery, or UI state.
 * A raw intent is encrypted in the durable outbox before crypto mutation and replaced by the exact
 * ciphertext before HTTP. The outbox key is also the stable GenericMessage ID, so uncertain retries
 * remain at-least-once on the wire but are idempotent at the receiving calling handler.
 */
@ExperimentalKaliumServiceApi
@Suppress("LongParameterList", "LargeClass")
public class WireEncryptedCallingSignalSender(
    private val identity: ServiceIdentity,
    private val networkOwner: JvmServiceNetworkOwner,
    private val crypto: WireServiceCryptoRuntime,
    private val contextProvider: ConversationContextProvider,
    private val outbox: CallingSignalOutbox,
) : EncryptedCallingSignalSender {
    private val outboxMutex = Mutex()

    override suspend fun send(signal: OutgoingCallingSignal): CallingResult = try {
        outboxMutex.withLock {
            val id = signal.idempotencyKey?.let { key ->
                runCatching { UUID.fromString(key).toString() }
                    .getOrElse { UUID.nameUUIDFromBytes(key.encodeToByteArray()).toString() }
            }
                ?: UUID.randomUUID().toString()
            val retained = loadSignals()[id]?.let(StoredSignalCodec::decode)
            val stored = retained ?: StoredSignal.Pending(signal).also { persist(id, it) }
            sendStored(id, stored)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        CallingResult.Failure(CallingFailure.Transport("Unable to send encrypted Wire calling signalling", failure))
    }

    /** Flushes exact retained ciphertext (or finishes a retained pre-encryption intent) during crypto startup. */
    public suspend fun flushPending(): CallingResult = try {
        outboxMutex.withLock {
            loadSignals().forEach { (id, payload) ->
                when (val result = sendStored(id, StoredSignalCodec.decode(payload))) {
                    is CallingResult.Failure -> return result
                    CallingResult.Success -> Unit
                }
            }
            CallingResult.Success
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        CallingResult.Failure(CallingFailure.State("Unable to flush the durable calling outbox", failure))
    }

    @Suppress("CyclomaticComplexMethod", "ComplexCondition")
    private suspend fun sendStored(id: String, stored: StoredSignal, allowRecipientRefresh: Boolean = true): CallingResult {
        val ready = when (stored) {
            is StoredSignal.Pending -> encrypt(id, stored.signal)
            is StoredSignal.Mls,
            is StoredSignal.Proteus -> stored
        }
        if (stored is StoredSignal.Pending) persist(id, ready)
        val sent = when (ready) {
            is StoredSignal.Mls -> networkOwner.requireNetwork().mlsMessageApi.sendMessage(ready.ciphertext)
            is StoredSignal.Proteus -> networkOwner.requireNetwork().messageApi.qualifiedSendMessage(
                Parameters.QualifiedDefaultParameters(
                    sender = identity.clientId,
                    recipients = ready.recipients,
                    nativePush = true,
                    priority = MessagePriority.HIGH,
                    transient = ready.transient,
                    externalBlob = null,
                    messageOption = if (ready.targeted) QualifiedMessageOption.IgnoreAll else QualifiedMessageOption.ReportAll,
                ),
                ready.conversationId.toNetwork(),
            )
            is StoredSignal.Pending -> error("Pending calling signal was not encrypted")
        }
        return when (sent) {
            is NetworkResponse.Error -> if (
                allowRecipientRefresh &&
                ready is StoredSignal.Proteus &&
                !ready.targeted &&
                sent.kException is ProteusClientsChangedError
            ) {
                val intent = ready.signal ?: return CallingResult.Failure(
                    CallingFailure.State(
                        "Legacy retained calling ciphertext cannot be refreshed after Proteus client churn",
                    ),
                )
                val refreshed = encrypt(id, intent) as StoredSignal.Proteus
                persist(id, refreshed)
                sendStored(id, refreshed, allowRecipientRefresh = false)
            } else {
                CallingResult.Failure(
                    CallingFailure.Transport("Wire rejected encrypted calling signalling", sent.kException),
                )
            }
            is NetworkResponse.Success -> {
                remove(id)
                CallingResult.Success
            }
        }
    }

    private suspend fun encrypt(messageId: String, signal: OutgoingCallingSignal): StoredSignal {
        val genericMessage = WireCallingMessageCodec.encode(
            messageId,
            signal.content,
            signal.callHostConversationId,
        )
        return when (val protocol = signal.protocol) {
            is CallConversationProtocol.Mls -> {
                val groupId = protocol.groupId.value
                val ciphertext = crypto.withMls("service-encrypt-calling-signal") { context ->
                    require(context.conversationExists(groupId)) { "The MLS calling group is not present in durable CoreCrypto state" }
                    context.commitPendingProposals(groupId)
                    context.encryptMessage(groupId, genericMessage)
                }
                StoredSignal.Mls(ciphertext)
            }
            CallConversationProtocol.Proteus,
            is CallConversationProtocol.Mixed -> encryptProteus(signal, genericMessage)
        }
    }

    private suspend fun encryptProteus(signal: OutgoingCallingSignal, plaintext: ByteArray): StoredSignal.Proteus {
        val clients = resolveRecipients(signal)
        require(clients.isNotEmpty()) { "Calling signalling has no resolved Wire recipients" }
        val sessionIds = clients.distinctBy { Triple(it.userId.value, it.userId.domain, it.clientId) }.map {
            CryptoSessionId(CryptoQualifiedID(it.userId.value, it.userId.domain), CryptoClientId(it.clientId))
        }
        val missing = crypto.withProteus("service-find-calling-sessions") { context ->
            sessionIds.filterNot { context.doesSessionExist(it) }
        }
        if (missing.isNotEmpty()) createMissingSessions(missing)
        val encrypted = crypto.withProteus("service-encrypt-proteus-calling-signal") { context ->
            context.encryptBatched(plaintext, sessionIds)
        }
        val recipients = encrypted.entries.groupBy { it.key.userId }.mapKeys { (user, _) ->
            NetworkQualifiedId(user.value, user.domain)
        }.mapValues { (_, entries) ->
            entries.associate { it.key.cryptoClientId.value to it.value }
        }
        val target = signal.target
        return StoredSignal.Proteus(
            signal = signal,
            conversationId = signal.transportConversationId,
            recipients = recipients,
            transient = signal.isTransient,
            targeted = target is CallSignalTarget.Conversation && target.recipients != null,
        )
    }

    private suspend fun resolveRecipients(signal: OutgoingCallingSignal): List<CallClient> = when (val target = signal.target) {
        is CallSignalTarget.Conversation -> target.recipients ?: resolveContextClients(signal.transportConversationId)
        CallSignalTarget.SelfClients -> resolveContextClients(signal.transportConversationId).filter {
            it.userId == identity.userId
        }
    }

    private suspend fun resolveContextClients(conversationId: ConversationId): List<CallClient> =
        when (val result = contextProvider.getForCall(conversationId)) {
            is ConversationContextResult.Failure -> error("Unable to resolve signalling recipients: ${result.failure}")
            is ConversationContextResult.Success -> result.context.clients
        }

    private suspend fun createMissingSessions(missing: List<CryptoSessionId>) {
        val request = missing.groupBy { it.userId.domain }.mapValues { (_, byDomain) ->
            byDomain.groupBy { it.userId.value }.mapValues { (_, byUser) -> byUser.map { it.cryptoClientId.value } }
        }
        val prekeys = when (val response = networkOwner.requireNetwork().preKeyApi.getUsersPreKey(request)) {
            is NetworkResponse.Error -> throw response.kException
            is NetworkResponse.Success -> response.value.qualifiedUserClientPrekeys
        }
        crypto.withProteus("service-create-calling-sessions") { context ->
            missing.forEach { sessionId ->
                val prekey = requireNotNull(
                    prekeys[sessionId.userId.domain]?.get(sessionId.userId.value)?.get(sessionId.cryptoClientId.value),
                ) { "The backend returned no Proteus prekey for a calling recipient" }
                context.createSession(PreKeyCrypto(prekey.id, prekey.key), sessionId)
            }
        }
    }

    private suspend fun persist(id: String, signal: StoredSignal) {
        when (val result = outbox.putSignal(id, StoredSignalCodec.encode(signal))) {
            is EncryptedServiceStateResult.Failure -> throw result.cause ?: IllegalStateException(result.description)
            is EncryptedServiceStateResult.Success -> Unit
        }
    }

    private suspend fun loadSignals(): Map<String, ByteArray> = when (val result = outbox.loadSignals()) {
        is EncryptedServiceStateResult.Failure -> throw result.cause ?: IllegalStateException(result.description)
        is EncryptedServiceStateResult.Success -> result.value
    }

    private suspend fun remove(id: String) {
        when (val result = outbox.removeSignal(id)) {
            is EncryptedServiceStateResult.Failure -> throw result.cause ?: IllegalStateException(result.description)
            is EncryptedServiceStateResult.Success -> Unit
        }
    }

    private fun ConversationId.toNetwork(): NetworkQualifiedId = NetworkQualifiedId(value, domain)
}

private sealed interface StoredSignal {
    data class Pending(val signal: OutgoingCallingSignal) : StoredSignal

    data class Mls(val ciphertext: ByteArray) : StoredSignal

    data class Proteus(
        val signal: OutgoingCallingSignal?,
        val conversationId: ConversationId,
        val recipients: Map<NetworkQualifiedId, Map<String, ByteArray>>,
        val transient: Boolean,
        val targeted: Boolean,
    ) : StoredSignal
}

@Suppress("MagicNumber", "NestedBlockDepth", "TooManyFunctions")
private object StoredSignalCodec {
    private const val VERSION = 2
    private const val LEGACY_VERSION = 1
    private const val MAX_COUNT = 100_000

    // The encrypted store caps the complete encoded outbox entry at 8 MiB.
    private const val MAX_BYTES = 8 * 1024 * 1024 - 64 * 1024

    fun encode(signal: StoredSignal): ByteArray = ByteArrayOutputStream().let { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            when (signal) {
                is StoredSignal.Pending -> {
                    output.writeByte(1)
                    output.writePending(signal.signal)
                }
                is StoredSignal.Mls -> {
                    output.writeByte(2)
                    output.writeBytes(signal.ciphertext)
                }
                is StoredSignal.Proteus -> {
                    output.writeByte(3)
                    output.writeBoolean(signal.signal != null)
                    signal.signal?.let { output.writePending(it) }
                    output.writeQualifiedId(signal.conversationId)
                    output.writeBoolean(signal.transient)
                    output.writeBoolean(signal.targeted)
                    output.writeInt(signal.recipients.size)
                    signal.recipients.forEach { (user, clients) ->
                        output.writeString(user.value)
                        output.writeString(user.domain)
                        output.writeInt(clients.size)
                        clients.forEach { (client, ciphertext) ->
                            output.writeString(client)
                            output.writeBytes(ciphertext)
                        }
                    }
                }
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StoredSignal = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val version = input.readInt()
        require(version == LEGACY_VERSION || version == VERSION) { "Unsupported calling outbox version" }
        val result = when (input.readUnsignedByte()) {
            1 -> StoredSignal.Pending(input.readPending())
            2 -> StoredSignal.Mls(input.readBytes())
            3 -> {
                val signal = if (version >= VERSION && input.readBoolean()) input.readPending() else null
                val conversation = input.readQualifiedId()
                val transient = input.readBoolean()
                val targeted = input.readBoolean()
                val recipients = linkedMapOf<NetworkQualifiedId, Map<String, ByteArray>>()
                repeat(input.readCount()) {
                    val user = NetworkQualifiedId(input.readString(), input.readString())
                    val clients = linkedMapOf<String, ByteArray>()
                    repeat(input.readCount()) { clients[input.readString()] = input.readBytes() }
                    recipients[user] = clients
                }
                StoredSignal.Proteus(signal, conversation, recipients, transient, targeted)
            }
            else -> error("Unknown calling outbox entry type")
        }
        require(input.available() == 0) { "Calling outbox entry contains trailing data" }
        result
    }

    private fun DataOutputStream.writePending(signal: OutgoingCallingSignal) {
        writeQualifiedId(signal.callHostConversationId)
        writeQualifiedId(signal.transportConversationId)
        writeString(signal.content)
        writeBoolean(signal.isTransient)
        writeProtocol(signal.protocol)
        when (val target = signal.target) {
            CallSignalTarget.SelfClients -> writeByte(1)
            is CallSignalTarget.Conversation -> {
                writeByte(if (target.recipients == null) 2 else 3)
                target.recipients?.let { clients ->
                    writeInt(clients.size)
                    clients.forEach {
                        writeQualifiedId(it.userId)
                        writeString(it.clientId)
                        writeBoolean(it.isMemberOfSubconversation)
                    }
                }
            }
        }
    }

    private fun DataInputStream.readPending(): OutgoingCallingSignal {
        val host = readQualifiedId()
        val transport = readQualifiedId()
        val content = readString()
        val transient = readBoolean()
        val protocol = readProtocol()
        val target = when (readUnsignedByte()) {
            1 -> CallSignalTarget.SelfClients
            2 -> CallSignalTarget.Conversation(null)
            3 -> CallSignalTarget.Conversation(
                List(readCount()) {
                    CallClient(readQualifiedId(), readString(), readBoolean())
                },
            )
            else -> error("Unknown calling signal target")
        }
        return OutgoingCallingSignal(host, transport, content, target, transient, protocol)
    }

    private fun DataOutputStream.writeProtocol(protocol: CallConversationProtocol) {
        when (protocol) {
            CallConversationProtocol.Proteus -> writeByte(1)
            is CallConversationProtocol.Mls -> {
                writeByte(2)
                writeString(protocol.groupId.value)
                writeNullableEpoch(protocol.epoch)
            }
            is CallConversationProtocol.Mixed -> {
                writeByte(3)
                writeString(protocol.groupId.value)
                writeNullableEpoch(protocol.epoch)
            }
        }
    }

    private fun DataInputStream.readProtocol(): CallConversationProtocol = when (readUnsignedByte()) {
        1 -> CallConversationProtocol.Proteus
        2 -> CallConversationProtocol.Mls(GroupID(readString()), readNullableEpoch())
        3 -> CallConversationProtocol.Mixed(GroupID(readString()), readNullableEpoch())
        else -> error("Unknown calling signal protocol")
    }

    private fun DataOutputStream.writeNullableEpoch(epoch: ULong?) {
        writeBoolean(epoch != null)
        epoch?.let { writeLong(it.toLong()) }
    }

    private fun DataInputStream.readNullableEpoch(): ULong? = if (readBoolean()) readLong().toULong() else null

    private fun DataOutputStream.writeQualifiedId(id: QualifiedID) {
        writeString(id.value)
        writeString(id.domain)
    }

    private fun DataInputStream.readQualifiedId(): QualifiedID = QualifiedID(readString(), readString())

    private fun DataOutputStream.writeString(value: String) = writeBytes(value.encodeToByteArray())

    private fun DataInputStream.readString(): String = readBytes().decodeToString()

    private fun DataOutputStream.writeBytes(value: ByteArray) {
        require(value.size <= MAX_BYTES) { "Calling outbox value is too large" }
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readBytes(): ByteArray {
        val size = readInt()
        require(size in 0..MAX_BYTES) { "Calling outbox value has an invalid size" }
        return ByteArray(size).also(::readFully)
    }

    private fun DataInputStream.readCount(): Int = readInt().also {
        require(it in 0..MAX_COUNT) { "Calling outbox collection has an invalid size" }
    }
}
