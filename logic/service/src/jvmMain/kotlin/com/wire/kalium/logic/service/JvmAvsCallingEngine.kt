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

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.callbacks.ClientsRequestHandler
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.callbacks.ConstantBitRateStateChangeHandler
import com.wire.kalium.calling.callbacks.EstablishedCallHandler
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.calling.callbacks.MetricsHandler
import com.wire.kalium.calling.callbacks.MissedCallHandler
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.callbacks.RequestNewEpochHandler
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.callbacks.VideoReceiveStateHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.calling.runtime.AvsCallingEngine
import com.wire.kalium.calling.runtime.AvsCallingEvent
import com.wire.kalium.calling.runtime.CallEpoch
import com.wire.kalium.calling.runtime.CallSignalTarget
import com.wire.kalium.calling.runtime.CallTransport
import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.NativeIncomingCall
import com.wire.kalium.calling.runtime.OutgoingCallingSignal
import com.wire.kalium.calling.runtime.SelfConversationResult
import com.wire.kalium.calling.runtime.ServiceSelfConversationProvider
import com.wire.kalium.calling.runtime.SftConnectionResult
import com.wire.kalium.conversation.CallClient
import com.wire.kalium.conversation.CallConversationContext
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.conversation.CallConversationType
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceIdentity
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val AVS_SFT_NO_RESPONSE_DATA = 1

/** Locally owned JVM AVS user runtime. Process setup/run/close are reference-counted across identities. */
@ExperimentalKaliumServiceApi
@Suppress("CyclomaticComplexMethod", "LongParameterList", "LargeClass", "ReturnCount", "TooManyFunctions")
public class JvmAvsCallingEngine(
    private val identity: ServiceIdentity,
    private val transport: CallTransport,
    private val contextProvider: ConversationContextProvider,
    private val conferenceMembership: WireConferenceMembership,
    private val deliveryJournal: AvsDeliveryJournal,
    private val serverTimeSeconds: suspend () -> Long,
    private val federationEnabled: Boolean,
    private val calling: Calling = Calling.INSTANCE,
    private val readyTimeoutMillis: Long = DEFAULT_READY_TIMEOUT_MILLIS,
    private val audioCbr: Boolean = false,
) : AvsCallingEngine {
    private val lifecycleMutex = Mutex()
    private val nativeCallbackMonitor = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = MutableSharedFlow<AvsCallingEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private val contexts = ConcurrentHashMap<ConversationId, CallConversationContext>()
    private val nativeConversationIds = ConcurrentHashMap<String, ConversationId>()
    private val pendingIncoming = ConcurrentHashMap.newKeySet<ConversationId>()
    private val active = ConcurrentHashMap.newKeySet<ConversationId>()
    private val nativeRequestNamespace = UUID.randomUUID().toString()

    /**
     * Coalesces concurrent delivery and remembers native acceptance for this AVS handle lifetime.
     * [deliveryJournal] closes the restart window with a durable pre-call intent and acceptance
     * record; an intent left ambiguous by a crash is never replayed automatically.
     */
    private val acceptedDeliveries = ConcurrentHashMap<String, CompletableDeferred<CallingResult>>()

    private var callbacks: CallbackBundle? = null

    @Volatile
    private var handle: Handle? = null

    @Volatile
    private var selfConversationProvider: ServiceSelfConversationProvider? = null

    @Volatile
    private var readyForCalls: Boolean = false
    private var processAcquired: Boolean = false
    private var closed: Boolean = false

    init {
        require(readyTimeoutMillis > 0) { "readyTimeoutMillis must be positive" }
    }

    override fun observeEvents(): Flow<AvsCallingEvent> = events.asSharedFlow()

    override suspend fun start(selfConversationProvider: ServiceSelfConversationProvider): CallingResult = lifecycleMutex.withLock {
        if (closed) return@withLock CallingResult.Failure(CallingFailure.RuntimeClosed)
        if (readyForCalls) return@withLock CallingResult.Success
        if (handle != null || processAcquired) {
            try {
                releaseNative()
            } catch (failure: Throwable) {
                return@withLock CallingResult.Failure(
                    CallingFailure.Avs("Unable to retry cleanup from a previous AVS startup", failure),
                )
            }
        }
        val ready = CompletableDeferred<Unit>()
        return@withLock try {
            ProcessRuntime.acquire(calling)
            processAcquired = true
            this.selfConversationProvider = selfConversationProvider
            val retainedCallbacks = createCallbacks(ready)
            val created = calling.wcall_create(
                identity.userId.toAvsId(),
                identity.clientId,
                retainedCallbacks.ready,
                retainedCallbacks.send,
                retainedCallbacks.sft,
                retainedCallbacks.incoming,
                retainedCallbacks.missed,
                retainedCallbacks.answered,
                retainedCallbacks.established,
                retainedCallbacks.closed,
                retainedCallbacks.metrics,
                retainedCallbacks.config,
                retainedCallbacks.cbr,
                retainedCallbacks.video,
                null,
            )
            // JNA populates IntegerType through setValue after constructing Uint32_t, so the
            // Kotlin constructor property can remain zero even when the native value is valid.
            check(created.toLong() != 0L) { "AVS returned an invalid user handle" }
            callbacks = retainedCallbacks
            handle = created
            calling.wcall_set_req_clients_handler(created, retainedCallbacks.clients)
            calling.wcall_set_req_new_epoch_handler(created, retainedCallbacks.newEpoch)
            val becameReady = withTimeoutOrNull(readyTimeoutMillis) {
                ready.await()
                true
            } ?: false
            check(becameReady) { "Timed out waiting for AVS readiness" }
            readyForCalls = true
            CallingResult.Success
        } catch (cancellation: CancellationException) {
            runCatching(::releaseNative).exceptionOrNull()?.let(cancellation::addSuppressed)
            throw cancellation
        } catch (failure: Throwable) {
            runCatching(::releaseNative).exceptionOrNull()?.let(failure::addSuppressed)
            CallingResult.Failure(CallingFailure.Avs("Unable to start the JVM AVS runtime", failure))
        }
    }

    override suspend fun join(context: CallConversationContext): CallingResult = lifecycleMutex.withLock {
        val native = handle.takeIf { readyForCalls } ?: return@withLock CallingResult.Failure(unavailableFailure())
        val conversationId = context.conversationId
        rememberContext(context)
        val avsConversationId = conversationId.toAvsId()
        val answeringIncoming = conversationId in pendingIncoming
        val result = try {
            if (answeringIncoming) {
                calling.wcall_answer(
                    native,
                    avsConversationId,
                    CallTypeCalling.AUDIO.avsValue,
                    audioCbr.toAvsInt(),
                )
            } else {
                calling.wcall_start(
                    native,
                    avsConversationId,
                    CallTypeCalling.AUDIO.avsValue,
                    context.toAvsConversationType(),
                    audioCbr.toAvsInt(),
                    context.isMeeting().toAvsInt(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return@withLock CallingResult.Failure(CallingFailure.Avs("AVS join invocation failed", failure))
        }
        return@withLock if (result == AVS_SUCCESS) {
            if (answeringIncoming) pendingIncoming.remove(conversationId)
            active += conversationId
            CallingResult.Success
        } else {
            CallingResult.Failure(CallingFailure.Avs("AVS rejected join with code $result"))
        }
    }

    @Suppress("NestedBlockDepth")
    override suspend fun receive(
        payload: com.wire.kalium.calling.runtime.IncomingCallingPayload,
        context: CallConversationContext,
        eventIdempotencyKey: String,
    ): CallingResult {
        if (eventIdempotencyKey.isBlank()) {
            return CallingResult.Failure(CallingFailure.State("Calling event idempotency key must not be blank"))
        }
        val ownedDelivery = CompletableDeferred<CallingResult>()
        val existingDelivery = acceptedDeliveries.putIfAbsent(eventIdempotencyKey, ownedDelivery)
        if (existingDelivery != null) return existingDelivery.await()
        var reservationAcquired = false
        var nativeAccepted = false
        return try {
            val result = when (val reservation = deliveryJournal.reserveDelivery(eventIdempotencyKey)) {
                AvsDeliveryReservationResult.Acquired -> {
                    reservationAcquired = true
                    when (val delivered = deliverToAvs(payload, context)) {
                        NativeDeliveryResult.Accepted -> {
                            nativeAccepted = true
                            when (val accepted = deliveryJournal.markDeliveryAccepted(eventIdempotencyKey)) {
                                is EncryptedServiceStateResult.Failure -> CallingResult.Failure(
                                    CallingFailure.State(accepted.description, accepted.cause),
                                )
                                is EncryptedServiceStateResult.Success -> CallingResult.Success
                            }
                        }
                        is NativeDeliveryResult.NotAccepted -> releaseReservation(eventIdempotencyKey, delivered.failure)
                        is NativeDeliveryResult.Ambiguous -> CallingResult.Failure(delivered.failure)
                    }
                }
                AvsDeliveryReservationResult.AlreadyAccepted -> CallingResult.Success
                AvsDeliveryReservationResult.Ambiguous -> CallingResult.Failure(
                    CallingFailure.State(
                        "AVS delivery is crash-ambiguous; operator evidence is required before replay",
                    ),
                )
                is AvsDeliveryReservationResult.Failure -> CallingResult.Failure(
                    CallingFailure.State(reservation.description, reservation.cause),
                )
            }
            result.also {
                ownedDelivery.complete(result)
                if (result is CallingResult.Failure) acceptedDeliveries.remove(eventIdempotencyKey, ownedDelivery)
            }
        } catch (cancellation: CancellationException) {
            if (reservationAcquired && !nativeAccepted) {
                withContext(NonCancellable) { deliveryJournal.releaseDelivery(eventIdempotencyKey) }
            }
            acceptedDeliveries.remove(eventIdempotencyKey, ownedDelivery)
            ownedDelivery.cancel(cancellation)
            throw cancellation
        } catch (failure: Throwable) {
            val result = CallingResult.Failure(CallingFailure.Avs("Unable to deliver calling signalling to AVS", failure))
            acceptedDeliveries.remove(eventIdempotencyKey, ownedDelivery)
            ownedDelivery.complete(result)
            result
        }
    }

    private suspend fun deliverToAvs(
        payload: com.wire.kalium.calling.runtime.IncomingCallingPayload,
        context: CallConversationContext,
    ): NativeDeliveryResult {
        val currentTime = try {
            serverTimeSeconds().toUInt32("server time")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return NativeDeliveryResult.NotAccepted(
                CallingFailure.Avs("Unable to resolve server time before AVS delivery", failure),
            )
        }
        val messageTime = try {
            payload.messageTimestampSeconds.toUInt32("calling message time")
        } catch (failure: Throwable) {
            return NativeDeliveryResult.NotAccepted(CallingFailure.Avs("Calling message time is invalid", failure))
        }
        val bytes = payload.content.encodeToByteArray()
        return lifecycleMutex.withLock {
            rememberContext(context)
            val native = handle?.takeIf { readyForCalls }
                ?: return@withLock NativeDeliveryResult.NotAccepted(unavailableFailure())
            try {
                val result = calling.wcall_recv_msg(
                    native,
                    bytes,
                    bytes.size,
                    currentTime,
                    messageTime,
                    payload.callHostConversationId.toAvsId(),
                    payload.senderUserId.toAvsId(),
                    payload.senderClientId,
                    context.toAvsConversationType(),
                    context.isMeeting().toAvsInt(),
                )
                if (result == AVS_SUCCESS) NativeDeliveryResult.Accepted else {
                    NativeDeliveryResult.NotAccepted(CallingFailure.Avs("AVS rejected calling signalling with code $result"))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                NativeDeliveryResult.Ambiguous(
                    CallingFailure.Avs("Native AVS delivery failed after invocation", failure),
                )
            }
        }
    }

    private suspend fun releaseReservation(key: String, failure: CallingFailure): CallingResult =
        when (val released = deliveryJournal.releaseDelivery(key)) {
            is EncryptedServiceStateResult.Failure -> CallingResult.Failure(
                CallingFailure.State(released.description, released.cause),
            )
            is EncryptedServiceStateResult.Success -> CallingResult.Failure(failure)
        }

    override suspend fun leave(conversationId: ConversationId): CallingResult = lifecycleMutex.withLock {
        val native = handle.takeIf { readyForCalls } ?: return@withLock CallingResult.Failure(unavailableFailure())
        if (conversationId !in active) {
            pendingIncoming.remove(conversationId)
            return@withLock CallingResult.Success
        }
        calling.wcall_end(native, conversationId.toAvsId())
        active.remove(conversationId)
        pendingIncoming.remove(conversationId)
        CallingResult.Success
    }

    override suspend fun updateEpoch(conversationId: ConversationId, epoch: CallEpoch): CallingResult =
        lifecycleMutex.withLock {
            val native = handle.takeIf { readyForCalls } ?: return@withLock CallingResult.Failure(unavailableFailure())
            val secret = epoch.secret.copyBytes()
            try {
                val result = calling.wcall_set_epoch_info(
                    native,
                    conversationId.toAvsId(),
                    epoch.epoch.toLong().toUInt32("MLS epoch"),
                    clientsJson(epoch.clients),
                    Base64.encode(secret),
                )
                if (result == AVS_SUCCESS) CallingResult.Success else {
                    CallingResult.Failure(CallingFailure.Avs("AVS rejected MLS epoch with code $result"))
                }
            } finally {
                secret.fill(0)
            }
        }

    override suspend fun recordAudio(path: String): CallingResult = lifecycleMutex.withLock {
        val native = handle.takeIf { readyForCalls } ?: return@withLock CallingResult.Failure(unavailableFailure())
        val target = Path.of(path)
        if (!target.isAbsolute || target.fileName == null) {
            return@withLock CallingResult.Failure(CallingFailure.State("Audio recording path must be an absolute file path"))
        }
        val parent = target.parent
        if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent)) {
            return@withLock CallingResult.Failure(CallingFailure.State("Audio recording directory is not writable"))
        }
        val result = calling.wcall_audio_record(native, target.toString())
        if (result == AVS_SUCCESS) CallingResult.Success else {
            CallingResult.Failure(CallingFailure.Avs("AVS rejected diagnostic audio recording with code $result"))
        }
    }

    override suspend fun close(): CallingResult = lifecycleMutex.withLock {
        if (closed && handle == null && !processAcquired) return@withLock CallingResult.Success
        closed = true
        readyForCalls = false
        scope.cancel()
        scope.coroutineContext[Job]?.join()
        var firstFailure: Throwable? = null
        try {
            val native = handle
            if (native != null) active.toList().forEach { calling.wcall_end(native, it.toAvsId()) }
        } catch (failure: Throwable) {
            firstFailure = failure
        }
        try {
            releaseNative()
        } catch (failure: Throwable) {
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        if (handle == null && !processAcquired) {
            contexts.clear()
            nativeConversationIds.clear()
            pendingIncoming.clear()
            active.clear()
            acceptedDeliveries.values.forEach { pending ->
                if (!pending.isCompleted) {
                    pending.complete(CallingResult.Failure(CallingFailure.RuntimeClosed))
                }
            }
            acceptedDeliveries.clear()
        }
        firstFailure?.let {
            CallingResult.Failure(CallingFailure.Avs("Unable to close the JVM AVS runtime", it))
        } ?: CallingResult.Success
    }

    @Suppress("LongMethod")
    private fun createCallbacks(readySignal: CompletableDeferred<Unit>): CallbackBundle {
        val ready = ReadyHandler { _, _ -> readySignal.complete(Unit) }
        val send = SendHandler { context, remoteConversationId, remoteSelfUserId, remoteClientId, recipients, destination, data,
            length, isTransient, myClientsOnly, _ ->
            if (
                !matchesSelf(remoteSelfUserId, remoteClientId) ||
                data == null ||
                !fitsDurableCallingEntry(length.value, recipients, destination)
            ) {
                AVS_INVALID_ARGUMENT
            } else {
                val payload = data.getByteArray(0, length.value.toInt()).decodeToString()
                val requestKey = UUID.nameUUIDFromBytes(
                    listOf(
                        nativeRequestNamespace,
                        context?.let(Pointer::nativeValue)?.toString().orEmpty(),
                        remoteConversationId,
                        recipients.orEmpty(),
                        destination.orEmpty(),
                        payload,
                    ).joinToString("\u0000").encodeToByteArray(),
                ).toString()
                scope.launch {
                    val result = sendFromAvs(
                        remoteConversationId,
                        recipients,
                        destination,
                        payload,
                        isTransient,
                        myClientsOnly,
                        requestKey,
                    )
                    lifecycleMutex.withLock {
                        val native = handle?.takeIf { readyForCalls } ?: return@withLock
                        when (result) {
                            CallingResult.Success -> calling.wcall_resp(native, HTTP_OK, "", context)
                            is CallingResult.Failure -> calling.wcall_resp(native, HTTP_BAD_REQUEST, "Calling signal failed", context)
                        }
                    }
                }
                AVS_SUCCESS
            }
        }
        val sft = SFTRequestHandler { context, url, data, _, _ ->
            if (data == null) {
                AVS_INVALID_ARGUMENT
            } else {
                // Match the Android client callback exactly: AVS supplies a NUL-terminated C
                // string here, while the accompanying length is not the JSON payload length.
                val payload = data.getString(0, Charsets.UTF_8.name()).encodeToByteArray()
                scope.launch {
                    val result = transport.connectToSft(url, payload)
                    lifecycleMutex.withLock {
                        val native = handle?.takeIf { readyForCalls } ?: return@withLock
                        when (result) {
                            is SftConnectionResult.Success -> calling.wcall_sft_resp(
                                native,
                                AVS_SUCCESS,
                                result.response,
                                result.response.size,
                                context,
                            )
                            is SftConnectionResult.Failure -> {
                                calling.wcall_sft_resp(
                                    native,
                                    AVS_SFT_NO_RESPONSE_DATA,
                                    ByteArray(0),
                                    0,
                                    context,
                                )
                                emitEvent(AvsCallingEvent.Failed(null, result.failure))
                            }
                        }
                    }
                }
                AVS_SUCCESS
            }
        }
        val incoming = IncomingCallHandler { conversation, messageTime, user, client, video, ring, _, _ ->
            val conversationId = resolveConversationId(conversation)
            pendingIncoming += conversationId
            emitEvent(
                AvsCallingEvent.Incoming(
                    NativeIncomingCall(
                        conversationId,
                        parseId(user, identity.userId.domain),
                        client,
                        messageTime.value,
                        video,
                        ring,
                    ),
                ),
            )
        }
        val missed = MissedCallHandler { conversation, _, _, _, _ ->
            emitEvent(AvsCallingEvent.Missed(resolveConversationId(conversation)))
        }
        val answered = AnsweredCallHandler { conversation, _ ->
            val id = resolveConversationId(conversation)
            pendingIncoming.remove(id)
            emitEvent(AvsCallingEvent.Answered(id))
        }
        val established = EstablishedCallHandler { conversation, _, _, _ ->
            emitEvent(AvsCallingEvent.Established(resolveConversationId(conversation)))
        }
        val closedCallback = CloseCallHandler { reason, conversation, _, _, _, _ ->
            val id = resolveConversationId(conversation)
            active.remove(id)
            pendingIncoming.remove(id)
            emitEvent(AvsCallingEvent.Closed(id, reason.toString()))
        }
        val metrics = MetricsHandler { _, _, _ -> Unit }
        val config = CallConfigRequestHandler { native, _ ->
            scope.launch {
                val result = transport.getCallConfig(null)
                synchronized(nativeCallbackMonitor) {
                    // The callback's native instance is authoritative. Handle is a JNA
                    // IntegerType whose Kotlin constructor property is not a reliable identity.
                    if (closed) return@synchronized
                    when (result) {
                        is com.wire.kalium.calling.runtime.CallConfigResult.Success -> calling.wcall_config_update(
                            native,
                            AVS_SUCCESS,
                            result.config.payload,
                        )
                        is com.wire.kalium.calling.runtime.CallConfigResult.Failure -> calling.wcall_config_update(
                            native,
                            AVS_TRANSPORT_ERROR,
                            "{}",
                        )
                    }
                }
            }
            AVS_SUCCESS
        }
        val cbr = ConstantBitRateStateChangeHandler { _, _, _, _ -> Unit }
        val video = VideoReceiveStateHandler { _, _, _, _, _ -> Unit }
        val clients = object : ClientsRequestHandler {
            override fun onClientsRequest(inst: Handle, conversationId: String, arg: Pointer?) {
                scope.launch { updateClients(inst, conversationId) }
            }
        }
        val newEpoch = object : RequestNewEpochHandler {
            override fun onRequestNewEpoch(inst: Handle, conversationId: String, arg: Pointer?) {
                scope.launch {
                    val id = resolveConversationId(conversationId)
                    val result = lifecycleMutex.withLock {
                        if (!readyForCalls || handle != inst) return@withLock null
                        conferenceMembership.advanceEpoch(id)
                    } ?: return@launch
                    if (result is CallingResult.Failure) events.emit(AvsCallingEvent.Failed(id, result.failure))
                }
            }
        }
        return CallbackBundle(
            ready,
            send,
            sft,
            incoming,
            missed,
            answered,
            established,
            closedCallback,
            metrics,
            config,
            cbr,
            video,
            clients,
            newEpoch,
        )
    }

    @Suppress("LongParameterList")
    private suspend fun sendFromAvs(
        nativeConversationId: String,
        recipientsJson: String?,
        clientDestination: String?,
        content: String,
        transient: Boolean,
        myClientsOnly: Boolean,
        requestKey: String,
    ): CallingResult {
        val callHost = resolveConversationId(nativeConversationId)
        if (myClientsOnly) {
            val provider = selfConversationProvider
                ?: return CallingResult.Failure(CallingFailure.State("Self-conversation provider is unavailable"))
            val targets = when (val result = provider.getSelfConversations()) {
                is SelfConversationResult.Failure -> return CallingResult.Failure(
                    CallingFailure.Conversation(result.description, result.cause),
                )
                is SelfConversationResult.Success -> result.targets
            }
            for (target in targets) {
                when (
                    val result = transport.sendSignal(
                        OutgoingCallingSignal(
                            callHost,
                            target.conversationId,
                            content,
                            CallSignalTarget.SelfClients,
                            transient,
                            target.protocol,
                            "$requestKey:${target.conversationId}",
                        ),
                    )
                ) {
                    is CallingResult.Failure -> return result
                    CallingResult.Success -> Unit
                }
            }
            return CallingResult.Success
        }

        val callContext = when (val known = contexts[callHost]) {
            null -> when (val resolved = contextProvider.getForCall(callHost)) {
                is ConversationContextResult.Failure -> return CallingResult.Failure(
                    CallingFailure.Conversation(resolved.failure.toString()),
                )
                is ConversationContextResult.Success -> resolved.context.also(::rememberContext)
            }
            else -> known
        }
        val recipients = recipientsJson?.let(::parseClients)
            ?: clientDestination?.let { destination -> callContext.clients.filter { it.clientId == destination } }
        return transport.sendSignal(
            OutgoingCallingSignal(
                callHost,
                callHost,
                content,
                CallSignalTarget.Conversation(recipients),
                transient,
                callContext.protocol,
                requestKey,
            ),
        )
    }

    private suspend fun updateClients(native: Handle, nativeConversationId: String) {
        val id = resolveConversationId(nativeConversationId)
        val context = when (val result = contextProvider.getForCall(id)) {
            is ConversationContextResult.Failure -> {
                events.emit(AvsCallingEvent.Failed(id, CallingFailure.Conversation(result.failure.toString())))
                return
            }
            is ConversationContextResult.Success -> result.context.also(::rememberContext)
        }
        val result = lifecycleMutex.withLock {
            if (!readyForCalls || handle != native) return@withLock AVS_TRANSPORT_ERROR
            calling.wcall_set_clients_for_conv(native, id.toAvsId(), clientsJson(context.clients))
        }
        if (result != AVS_SUCCESS) {
            events.emit(AvsCallingEvent.Failed(id, CallingFailure.Avs("AVS rejected client list with code $result")))
            return
        }
        if (context.protocol is CallConversationProtocol.Mls) {
            val epoch = try {
                conferenceMembership.currentEpoch(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.emit(AvsCallingEvent.Failed(id, CallingFailure.Crypto("Unable to refresh MLS call membership", failure)))
                return
            }
            if (epoch != null) {
                when (val epochResult = updateEpoch(id, epoch)) {
                    is CallingResult.Failure -> events.emit(AvsCallingEvent.Failed(id, epochResult.failure))
                    CallingResult.Success -> Unit
                }
            }
        }
    }

    private fun parseClients(json: String): List<CallClient> {
        val clients = Json.parseToJsonElement(json).jsonObject["clients"]?.jsonArray ?: JsonArray(emptyList())
        return clients.map { element ->
            val objectValue = element.jsonObject
            val user = requireNotNull(objectValue["userid"]?.jsonPrimitive?.contentOrNull)
            val client = requireNotNull(objectValue["clientid"]?.jsonPrimitive?.contentOrNull)
            CallClient(
                parseId(user, identity.userId.domain),
                client,
                objectValue["in_subconv"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    private fun clientsJson(clients: List<CallClient>): String = buildJsonObject {
        put(
            "clients",
            buildJsonArray {
                clients.forEach { client ->
                    add(
                        buildJsonObject {
                            put("userid", client.userId.toAvsId())
                            put("clientid", client.clientId)
                            put("in_subconv", client.isMemberOfSubconversation)
                        },
                    )
                }
            },
        )
    }.toString()

    private fun rememberContext(context: CallConversationContext) {
        contexts[context.conversationId] = context
        nativeConversationIds[context.conversationId.toAvsId()] = context.conversationId
        nativeConversationIds[context.conversationId.value] = context.conversationId
    }

    private fun emitEvent(event: AvsCallingEvent) {
        if (!events.tryEmit(event)) scope.launch { events.emit(event) }
    }

    private fun resolveConversationId(value: String): ConversationId = nativeConversationIds[value]
        ?: parseId(value, identity.userId.domain).also { nativeConversationIds[value] = it }

    private fun parseId(value: String, fallbackDomain: String): QualifiedID {
        val separator = value.lastIndexOf('@')
        return if (separator > 0 && separator < value.lastIndex) {
            QualifiedID(value.substring(0, separator), value.substring(separator + 1))
        } else {
            QualifiedID(value, fallbackDomain)
        }
    }

    private fun matchesSelf(userId: String, clientId: String): Boolean =
        (userId == identity.userId.value || userId == identity.userId.toString()) && clientId == identity.clientId

    private fun QualifiedID.toAvsId(): String = if (federationEnabled) toString() else value

    private fun CallConversationContext.toAvsConversationType(): Int = when {
        protocol is CallConversationProtocol.Mls -> ConversationTypeCalling.ConferenceMls.avsValue
        type == CallConversationType.ONE_TO_ONE -> ConversationTypeCalling.OneOnOne.avsValue
        else -> ConversationTypeCalling.Conference.avsValue
    }

    private fun CallConversationContext.isMeeting(): Boolean = type == CallConversationType.MEETING

    private fun Boolean.toAvsInt(): Int = if (this) 1 else 0

    private fun fitsDurableCallingEntry(length: Long, recipientsJson: String?, destination: String?): Boolean {
        if (length !in 1..MAX_NATIVE_PAYLOAD_BYTES) return false
        val variableCodecBytes = recipientsJson?.encodeToByteArray()?.size.orZero() +
                destination?.encodeToByteArray()?.size.orZero()
        return length + variableCodecBytes <= MAX_ENCRYPTED_STORE_BLOB_BYTES - MIN_CALLING_CODEC_OVERHEAD_BYTES
    }

    private fun Long.toUInt32(name: String): Uint32_t {
        require(this in 0..UINT32_MAX) { "$name is outside AVS uint32 range" }
        return Uint32_t(this)
    }

    private fun unavailableFailure(): CallingFailure = if (closed) CallingFailure.RuntimeClosed else CallingFailure.RuntimeNotReady

    private fun releaseNative() {
        readyForCalls = false
        synchronized(nativeCallbackMonitor) {
            val native = handle
            if (native != null) {
                // Native code may still call any registered handler until destroy completes.
                // Keep the entire callback bundle strongly reachable when destroy fails so close can retry.
                calling.wcall_destroy(native)
                handle = null
                callbacks = null
            }
        }
        if (processAcquired) {
            ProcessRuntime.release(calling)
            processAcquired = false
        }
    }

    private data class CallbackBundle(
        val ready: ReadyHandler,
        val send: SendHandler,
        val sft: SFTRequestHandler,
        val incoming: IncomingCallHandler,
        val missed: MissedCallHandler,
        val answered: AnsweredCallHandler,
        val established: EstablishedCallHandler,
        val closed: CloseCallHandler,
        val metrics: MetricsHandler,
        val config: CallConfigRequestHandler,
        val cbr: ConstantBitRateStateChangeHandler,
        val video: VideoReceiveStateHandler,
        val clients: ClientsRequestHandler,
        val newEpoch: RequestNewEpochHandler,
    )

    private sealed interface NativeDeliveryResult {
        data object Accepted : NativeDeliveryResult
        data class NotAccepted(val failure: CallingFailure) : NativeDeliveryResult
        data class Ambiguous(val failure: CallingFailure) : NativeDeliveryResult
    }

    private object ProcessRuntime {
        private val monitor = Any()
        private var users: Int = 0

        fun acquire(calling: Calling): Unit = synchronized(monitor) {
            if (users == 0) {
                val setup = calling.wcall_setup()
                if (setup != AVS_SUCCESS) {
                    calling.wcall_close()
                    error("AVS setup failed with code $setup")
                }
                val run = calling.wcall_run()
                if (run != AVS_SUCCESS) {
                    calling.wcall_close()
                    error("AVS run failed with code $run")
                }
                calling.wcall_set_log_handler(SilentNativeLogHandler, null)
            }
            users += 1
        }

        fun release(calling: Calling): Unit = synchronized(monitor) {
            if (users == 0) return
            if (users > 1) {
                users -= 1
            } else {
                // Keep the final reference when global close fails, allowing the same engine to retry.
                calling.wcall_close()
                users = 0
            }
        }
    }

    private object SilentNativeLogHandler : LogHandler {
        override fun onLog(level: Int, message: String, arg: Pointer?) = Unit
    }

    private companion object {
        const val DEFAULT_READY_TIMEOUT_MILLIS = 30_000L
        const val EVENT_BUFFER_CAPACITY = 64
        const val AVS_SUCCESS = 0
        const val AVS_INVALID_ARGUMENT = 22
        const val AVS_TRANSPORT_ERROR = 5
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400

        // The durable encrypted outbox accepts an 8 MiB encoded entry. Keep one MiB for
        // StoredSignalCodec IDs/protocol/recipient metadata, and also check actual recipient bytes.
        const val MAX_ENCRYPTED_STORE_BLOB_BYTES = 8L * 1024 * 1024
        const val MIN_CALLING_CODEC_OVERHEAD_BYTES = 4L * 1024
        const val MAX_NATIVE_PAYLOAD_BYTES = MAX_ENCRYPTED_STORE_BLOB_BYTES - 1L * 1024 * 1024
        const val UINT32_MAX = 0xFFFF_FFFFL
    }
}

private fun Int?.orZero(): Int = this ?: 0
