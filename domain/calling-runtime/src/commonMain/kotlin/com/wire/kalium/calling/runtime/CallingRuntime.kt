@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.event.processing.ExperimentalEventProcessingApi::class,
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.calling.runtime

import com.wire.kalium.conversation.CallClient
import com.wire.kalium.conversation.CallConversationContext
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.event.processing.EventHandler
import com.wire.kalium.event.processing.EventHandlerRequirement
import com.wire.kalium.event.processing.EventHandlingContext
import com.wire.kalium.event.processing.EventHandlingResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@ExperimentalCallingRuntimeApi
public enum class ActiveCallStatus {
    JOINING,
    ACTIVE,
    LEAVING,
}

@ExperimentalCallingRuntimeApi
public data class ActiveCall(
    public val conversationId: ConversationId,
    public val protocol: CallConversationProtocol,
    public val status: ActiveCallStatus,
)

@ExperimentalCallingRuntimeApi
public data class IncomingCallingPayload(
    public val callHostConversationId: ConversationId,
    public val transportConversationId: ConversationId,
    public val senderUserId: UserId,
    public val senderClientId: String,
    public val messageTimestampSeconds: Long,
    public val content: String,
    public val isSelfMessage: Boolean,
    /** Stable GenericMessage ID used to deduplicate a resend delivered in another backend envelope. */
    public val messageId: String? = null,
)

@ExperimentalCallingRuntimeApi
public data class NativeIncomingCall(
    public val conversationId: ConversationId,
    public val senderUserId: UserId,
    public val senderClientId: String,
    public val messageTimestampSeconds: Long,
    public val isVideoCall: Boolean,
    public val shouldRing: Boolean,
)

@ExperimentalCallingRuntimeApi
public sealed interface AvsCallingEvent {
    public data class Incoming(public val call: NativeIncomingCall) : AvsCallingEvent

    public data class Answered(public val conversationId: ConversationId) : AvsCallingEvent

    public data class Established(public val conversationId: ConversationId) : AvsCallingEvent

    public data class Missed(public val conversationId: ConversationId) : AvsCallingEvent

    public data class Closed(public val conversationId: ConversationId, public val reason: String?) : AvsCallingEvent

    public data class Failed(public val conversationId: ConversationId?, public val failure: CallingFailure) : AvsCallingEvent
}

@ExperimentalCallingRuntimeApi
public sealed interface CallSignalTarget {
    public data object SelfClients : CallSignalTarget

    public data class Conversation(public val recipients: List<CallClient>?) : CallSignalTarget
}

@ExperimentalCallingRuntimeApi
public data class SelfConversationTarget(
    public val conversationId: ConversationId,
    public val protocol: CallConversationProtocol,
)

@ExperimentalCallingRuntimeApi
public sealed interface SelfConversationResult {
    public data class Success(public val targets: List<SelfConversationTarget>) : SelfConversationResult

    public data class Failure(public val description: String, public val cause: Throwable? = null) : SelfConversationResult
}

/** Required durable or remotely resolvable self-conversation IDs for AVS my-clients signalling. */
@ExperimentalCallingRuntimeApi
public fun interface ServiceSelfConversationProvider {
    public suspend fun getSelfConversations(): SelfConversationResult
}

/** Plain calling content. The transport must encode and encrypt it before using Wire APIs. */
@ExperimentalCallingRuntimeApi
public data class OutgoingCallingSignal(
    public val callHostConversationId: ConversationId,
    public val transportConversationId: ConversationId,
    public val content: String,
    public val target: CallSignalTarget,
    public val isTransient: Boolean,
    public val protocol: CallConversationProtocol,
    /** Stable local request key used to coalesce a retried native send callback. */
    public val idempotencyKey: String? = null,
)

@ExperimentalCallingRuntimeApi
public sealed interface CallingFailure {
    public data object RuntimeNotReady : CallingFailure

    public data object RuntimeClosed : CallingFailure

    public data object AlreadyActive : CallingFailure

    public data object NotActive : CallingFailure

    public data object ConcurrencyLimitReached : CallingFailure

    public data class Conversation(public val description: String, public val cause: Throwable? = null) : CallingFailure

    public data class Crypto(public val description: String, public val cause: Throwable? = null) : CallingFailure

    public data class Avs(public val description: String, public val cause: Throwable? = null) : CallingFailure

    public data class Transport(public val description: String, public val cause: Throwable? = null) : CallingFailure

    public data class State(public val description: String, public val cause: Throwable? = null) : CallingFailure
}

@ExperimentalCallingRuntimeApi
public sealed interface CallingResult {
    public data object Success : CallingResult

    public data class Failure(public val failure: CallingFailure) : CallingResult
}

@ExperimentalCallingRuntimeApi
public data class CallConfig(public val payload: String)

@ExperimentalCallingRuntimeApi
public sealed interface CallConfigResult {
    public data class Success(public val config: CallConfig) : CallConfigResult

    public data class Failure(public val failure: CallingFailure) : CallConfigResult
}

@ExperimentalCallingRuntimeApi
public sealed interface SftConnectionResult {
    public data class Success(public val response: ByteArray) : SftConnectionResult

    public data class Failure(public val failure: CallingFailure) : SftConnectionResult
}

/** Authenticated HTTP plus encrypted Wire calling-signalling transport. */
@ExperimentalCallingRuntimeApi
public interface CallTransport {
    public suspend fun getCallConfig(limit: Int?): CallConfigResult

    public suspend fun connectToSft(url: String, payload: ByteArray): SftConnectionResult

    public suspend fun sendSignal(signal: OutgoingCallingSignal): CallingResult
}

/** Low-level AVS owner. Implementations must keep one engine per runtime identity. */
@ExperimentalCallingRuntimeApi
public interface AvsCallingEngine {
    /** Native lifecycle events emitted by this identity-owned engine. */
    public fun observeEvents(): Flow<AvsCallingEvent> = emptyFlow()

    /** Starts one locally owned native engine. Must not install process-global call ownership. */
    public suspend fun start(selfConversationProvider: ServiceSelfConversationProvider): CallingResult

    public suspend fun join(context: CallConversationContext): CallingResult

    /**
     * Delivers signalling at least once. Implementations must deduplicate non-idempotent effects
     * by [eventIdempotencyKey] because a process can stop after AVS accepts the payload but before
     * the durable calling-event store is updated.
     */
    public suspend fun receive(
        payload: IncomingCallingPayload,
        context: CallConversationContext,
        eventIdempotencyKey: String,
    ): CallingResult

    public suspend fun leave(conversationId: ConversationId): CallingResult

    /** Supplies MLS conference epoch key material and membership to AVS. */
    public suspend fun updateEpoch(conversationId: ConversationId, epoch: CallEpoch): CallingResult = CallingResult.Failure(
        CallingFailure.Avs("MLS epoch updates are not supported by this AVS engine"),
    )

    /** Starts raw PCM playout recording through the AVS record audio device. */
    public suspend fun recordAudio(path: String): CallingResult = CallingResult.Failure(
        CallingFailure.Avs("Audio recording is not supported by this AVS engine"),
    )

    public suspend fun close(): CallingResult
}

@ExperimentalCallingRuntimeApi
public class CallEpochSecret private constructor(private val value: ByteArray) {
    public fun copyBytes(): ByteArray = value.copyOf()

    override fun toString(): String = "CallEpochSecret(<redacted>)"

    public companion object {
        public fun fromBytes(value: ByteArray): CallEpochSecret = CallEpochSecret(value.copyOf())
    }
}

@ExperimentalCallingRuntimeApi
public data class CallEpoch(public val epoch: ULong, public val secret: CallEpochSecret, public val clients: List<CallClient>)

@ExperimentalCallingRuntimeApi
public interface ConferenceMembership {
    public suspend fun join(context: CallConversationContext): CallingResult

    /** Must be idempotent so partially completed shutdown can be retried. */
    public suspend fun leave(call: ActiveCall): CallingResult

    public fun observeEpochs(conversationId: ConversationId): Flow<CallEpoch>
}

@ExperimentalCallingRuntimeApi
public interface CallStateStore {
    public fun observeActiveCalls(): Flow<List<ActiveCall>>

    public suspend fun activeCalls(): List<ActiveCall>

    public suspend fun update(call: ActiveCall): CallingResult

    public suspend fun remove(conversationId: ConversationId): CallingResult
}

@ExperimentalCallingRuntimeApi
public sealed interface CallLifecycleEvent {
    public data class Joining(public val context: CallConversationContext) : CallLifecycleEvent

    public data class Joined(public val context: CallConversationContext) : CallLifecycleEvent

    public data class Incoming(public val call: NativeIncomingCall) : CallLifecycleEvent

    public data class Answered(public val conversationId: ConversationId) : CallLifecycleEvent

    public data class Established(public val conversationId: ConversationId) : CallLifecycleEvent

    public data class Missed(public val conversationId: ConversationId) : CallLifecycleEvent

    public data class SignallingReceived(
        public val payload: IncomingCallingPayload,
        public val eventIdempotencyKey: String,
    ) : CallLifecycleEvent

    public data class Leaving(public val conversationId: ConversationId) : CallLifecycleEvent

    public data class Left(public val conversationId: ConversationId) : CallLifecycleEvent

    public data class Closed(public val conversationId: ConversationId, public val reason: String?) : CallLifecycleEvent

    public data class Failed(public val conversationId: ConversationId?, public val failure: CallingFailure) : CallLifecycleEvent
}

@ExperimentalCallingRuntimeApi
public fun interface CallEventSink {
    public suspend fun emit(event: CallLifecycleEvent): CallingResult
}

@ExperimentalCallingRuntimeApi
public sealed interface CallingControlResult {
    public data object ForwardToAvs : CallingControlResult

    public data object Handled : CallingControlResult

    public data class Failure(public val failure: CallingFailure) : CallingControlResult
}

/** Preserves calling control messages such as REMOTEMUTE before AVS delivery. */
@ExperimentalCallingRuntimeApi
public fun interface CallingControlHandler {
    /** Uses [eventIdempotencyKey] to make control-message effects safe for at-least-once delivery. */
    public suspend fun handle(payload: IncomingCallingPayload, eventIdempotencyKey: String): CallingControlResult
}

@ExperimentalCallingRuntimeApi
public sealed interface CallingEventIdempotencyResult {
    public data class Found(public val handled: Boolean) : CallingEventIdempotencyResult

    public data class Failure(public val description: String, public val cause: Throwable? = null) : CallingEventIdempotencyResult
}

/** Durable per-identity calling-handler state. No no-op implementation is provided. */
@ExperimentalCallingRuntimeApi
public interface CallingEventIdempotencyStore {
    public suspend fun isHandled(key: String): CallingEventIdempotencyResult

    public suspend fun markHandled(key: String): CallingEventIdempotencyResult
}

@ExperimentalCallingRuntimeApi
public class InMemoryCallStateStore : CallStateStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<ActiveCall>>(emptyList())

    override fun observeActiveCalls(): Flow<List<ActiveCall>> = state.asStateFlow()

    override suspend fun activeCalls(): List<ActiveCall> = mutex.withLock { state.value }

    override suspend fun update(call: ActiveCall): CallingResult = mutex.withLock {
        state.value = state.value.filterNot { it.conversationId == call.conversationId } + call
        CallingResult.Success
    }

    override suspend fun remove(conversationId: ConversationId): CallingResult = mutex.withLock {
        state.value = state.value.filterNot { it.conversationId == conversationId }
        CallingResult.Success
    }
}

@ExperimentalCallingRuntimeApi
@Suppress("LongMethod", "LongParameterList", "TooManyFunctions")
public class CallingRuntime(
    private val contextProvider: ConversationContextProvider,
    private val transport: CallTransport,
    private val conferenceMembership: ConferenceMembership,
    private val engine: AvsCallingEngine,
    private val selfConversationProvider: ServiceSelfConversationProvider,
    private val controlHandler: CallingControlHandler,
    private val stateStore: CallStateStore,
    private val eventSink: CallEventSink,
    private val maxConcurrentCalls: Int,
) {
    private val operationMutex = Mutex()
    private val ownedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val epochJobs = mutableMapOf<ConversationId, Job>()
    private var engineEventsJob: Job? = null
    private var lifecycle = Lifecycle.CREATED

    init {
        require(maxConcurrentCalls > 0) { "maxConcurrentCalls must be positive" }
    }

    public fun observeActiveCalls(): Flow<List<ActiveCall>> = stateStore.observeActiveCalls()

    public suspend fun start(): CallingResult = operationMutex.withLock {
        when (lifecycle) {
            Lifecycle.CREATED -> when (val result = engine.start(selfConversationProvider)) {
                is CallingResult.Failure -> fail(null, result.failure)
                CallingResult.Success -> {
                    lifecycle = Lifecycle.OPEN
                    engineEventsJob = ownedScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        engine.observeEvents().collect(::handleAvsEvent)
                    }
                    CallingResult.Success
                }
            }
            Lifecycle.OPEN -> CallingResult.Success
            Lifecycle.CLOSING,
            Lifecycle.CLOSED -> CallingResult.Failure(CallingFailure.RuntimeClosed)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    public suspend fun join(conversationId: ConversationId): CallingResult = operationMutex.withLock {
        if (lifecycle != Lifecycle.OPEN) return@withLock CallingResult.Failure(unavailableFailure())
        val activeCalls = stateStore.activeCalls()
        if (activeCalls.any { it.conversationId == conversationId }) {
            return@withLock CallingResult.Failure(CallingFailure.AlreadyActive)
        }
        if (activeCalls.size >= maxConcurrentCalls) {
            return@withLock CallingResult.Failure(CallingFailure.ConcurrencyLimitReached)
        }
        val context = when (val result = contextProvider.getForCall(conversationId)) {
            is ConversationContextResult.Success -> result.context
            is ConversationContextResult.Failure -> return@withLock CallingResult.Failure(
                CallingFailure.Conversation(result.failure.toString()),
            )
        }
        emitSafely(CallLifecycleEvent.Joining(context))
        val joiningCall = ActiveCall(conversationId, context.protocol, ActiveCallStatus.JOINING)
        when (val stateResult = stateStore.update(joiningCall)) {
            is CallingResult.Failure -> return@withLock fail(conversationId, stateResult.failure)
            CallingResult.Success -> Unit
        }
        if (context.protocol.isMlsCapable()) {
            when (val membershipResult = conferenceMembership.join(context)) {
                is CallingResult.Failure -> {
                    stateStore.remove(conversationId)
                    return@withLock fail(conversationId, membershipResult.failure)
                }
                CallingResult.Success -> Unit
            }
        }
        when (val engineResult = engine.join(context)) {
            is CallingResult.Failure -> {
                if (context.protocol.isMlsCapable()) conferenceMembership.leave(joiningCall)
                stateStore.remove(conversationId)
                return@withLock fail(conversationId, engineResult.failure)
            }
            CallingResult.Success -> Unit
        }
        when (val stateResult = stateStore.update(joiningCall.copy(status = ActiveCallStatus.ACTIVE))) {
            is CallingResult.Failure -> {
                engine.leave(conversationId)
                if (context.protocol.isMlsCapable()) conferenceMembership.leave(joiningCall)
                stateStore.remove(conversationId)
                return@withLock fail(conversationId, stateResult.failure)
            }
            CallingResult.Success -> Unit
        }
        if (context.protocol.isMlsCapable()) {
            epochJobs[conversationId] = ownedScope.launch {
                conferenceMembership.observeEpochs(conversationId).collect { epoch ->
                    when (val result = engine.updateEpoch(conversationId, epoch)) {
                        CallingResult.Success -> Unit
                        is CallingResult.Failure -> emitSafely(CallLifecycleEvent.Failed(conversationId, result.failure))
                    }
                }
            }
        }
        emitSafely(CallLifecycleEvent.Joined(context))
        CallingResult.Success
    }

    @Suppress("ReturnCount")
    public suspend fun receive(payload: IncomingCallingPayload, eventIdempotencyKey: String): CallingResult {
        if (lifecycle != Lifecycle.OPEN) return CallingResult.Failure(unavailableFailure())
        val context = when (val result = contextProvider.getForCall(payload.callHostConversationId)) {
            is ConversationContextResult.Success -> result.context
            is ConversationContextResult.Failure -> return CallingResult.Failure(
                CallingFailure.Conversation(result.failure.toString()),
            )
        }
        val result = when (val controlResult = controlHandler.handle(payload, eventIdempotencyKey)) {
            CallingControlResult.ForwardToAvs -> engine.receive(payload, context, eventIdempotencyKey)
            CallingControlResult.Handled -> CallingResult.Success
            is CallingControlResult.Failure -> CallingResult.Failure(controlResult.failure)
        }
        return result.also {
            when (result) {
                CallingResult.Success -> emitSafely(CallLifecycleEvent.SignallingReceived(payload, eventIdempotencyKey))
                is CallingResult.Failure -> emitSafely(CallLifecycleEvent.Failed(payload.callHostConversationId, result.failure))
            }
        }
    }

    public suspend fun sendSignal(signal: OutgoingCallingSignal): CallingResult =
        if (lifecycle != Lifecycle.OPEN) CallingResult.Failure(unavailableFailure()) else transport.sendSignal(signal)

    public suspend fun getCallConfig(limit: Int?): CallConfigResult =
        if (lifecycle != Lifecycle.OPEN) CallConfigResult.Failure(unavailableFailure()) else transport.getCallConfig(limit)

    public suspend fun connectToSft(url: String, payload: ByteArray): SftConnectionResult =
        if (lifecycle != Lifecycle.OPEN) {
            SftConnectionResult.Failure(unavailableFailure())
        } else {
            transport.connectToSft(url, payload)
        }

    public suspend fun recordAudio(path: String): CallingResult =
        if (lifecycle != Lifecycle.OPEN) CallingResult.Failure(unavailableFailure()) else engine.recordAudio(path)

    public suspend fun leave(conversationId: ConversationId): CallingResult = operationMutex.withLock {
        leaveLocked(conversationId)
    }

    public suspend fun close(): CallingResult = operationMutex.withLock {
        if (lifecycle == Lifecycle.CLOSED) return@withLock CallingResult.Success
        lifecycle = Lifecycle.CLOSING
        var firstFailure: CallingResult.Failure? = null
        stateStore.activeCalls().forEach {
            val result = leaveLocked(it.conversationId, allowClosed = true)
            if (result is CallingResult.Failure && firstFailure == null) firstFailure = result
        }
        if (firstFailure != null) return@withLock firstFailure
        engineEventsJob?.cancelAndJoin()
        engineEventsJob = null
        epochJobs.values.forEach { it.cancel() }
        epochJobs.values.forEach { it.join() }
        epochJobs.clear()
        ownedScope.cancel()
        ownedScope.coroutineContext[Job]?.join()
        val engineResult = engine.close()
        if (engineResult is CallingResult.Failure) return@withLock engineResult
        lifecycle = Lifecycle.CLOSED
        CallingResult.Success
    }

    @Suppress("ReturnCount")
    private suspend fun leaveLocked(conversationId: ConversationId, allowClosed: Boolean = false): CallingResult {
        if (lifecycle != Lifecycle.OPEN && !allowClosed) return CallingResult.Failure(unavailableFailure())
        val call = stateStore.activeCalls().firstOrNull { it.conversationId == conversationId }
            ?: return CallingResult.Failure(CallingFailure.NotActive)
        val leavingStateResult = stateStore.update(call.copy(status = ActiveCallStatus.LEAVING))
        if (leavingStateResult is CallingResult.Failure) return fail(conversationId, leavingStateResult.failure)
        emitSafely(CallLifecycleEvent.Leaving(conversationId))
        var firstFailure: CallingFailure? = null
        val engineResult = engine.leave(conversationId)
        if (engineResult is CallingResult.Failure) firstFailure = engineResult.failure
        if (call.protocol.isMlsCapable()) {
            val membershipResult = conferenceMembership.leave(call)
            if (membershipResult is CallingResult.Failure && firstFailure == null) firstFailure = membershipResult.failure
        }
        return if (firstFailure == null) {
            epochJobs.remove(conversationId)?.cancel()
            when (val removeResult = stateStore.remove(conversationId)) {
                is CallingResult.Failure -> fail(conversationId, removeResult.failure)
                CallingResult.Success -> {
                    emitSafely(CallLifecycleEvent.Left(conversationId))
                    CallingResult.Success
                }
            }
        } else {
            stateStore.update(call.copy(status = ActiveCallStatus.ACTIVE))
            fail(conversationId, checkNotNull(firstFailure))
        }
    }

    private suspend fun fail(conversationId: ConversationId?, failure: CallingFailure): CallingResult.Failure {
        emitSafely(CallLifecycleEvent.Failed(conversationId, failure))
        return CallingResult.Failure(failure)
    }

    private suspend fun emitSafely(event: CallLifecycleEvent): CallingResult = try {
        eventSink.emit(event)
    } catch (failure: Throwable) {
        CallingResult.Failure(CallingFailure.State("Call event sink failed", failure))
    }

    private suspend fun handleAvsEvent(event: AvsCallingEvent) {
        when (event) {
            is AvsCallingEvent.Incoming -> emitSafely(CallLifecycleEvent.Incoming(event.call))
            is AvsCallingEvent.Answered -> emitSafely(CallLifecycleEvent.Answered(event.conversationId))
            is AvsCallingEvent.Established -> emitSafely(CallLifecycleEvent.Established(event.conversationId))
            is AvsCallingEvent.Missed -> emitSafely(CallLifecycleEvent.Missed(event.conversationId))
            is AvsCallingEvent.Failed -> emitSafely(CallLifecycleEvent.Failed(event.conversationId, event.failure))
            is AvsCallingEvent.Closed -> operationMutex.withLock {
                val call = stateStore.activeCalls().firstOrNull { it.conversationId == event.conversationId }
                epochJobs.remove(event.conversationId)?.cancel()
                val membershipResult = if (call?.protocol?.isMlsCapable() == true) {
                    conferenceMembership.leave(call)
                } else {
                    CallingResult.Success
                }
                if (membershipResult is CallingResult.Failure) {
                    emitSafely(CallLifecycleEvent.Failed(event.conversationId, membershipResult.failure))
                } else if (call != null) {
                    stateStore.remove(event.conversationId)
                }
                emitSafely(CallLifecycleEvent.Closed(event.conversationId, event.reason))
            }
        }
    }

    private fun unavailableFailure(): CallingFailure = when (lifecycle) {
        Lifecycle.CREATED -> CallingFailure.RuntimeNotReady
        Lifecycle.OPEN -> error("Runtime is available")
        Lifecycle.CLOSING,
        Lifecycle.CLOSED -> CallingFailure.RuntimeClosed
    }

    private enum class Lifecycle { CREATED, OPEN, CLOSING, CLOSED }
}

private fun CallConversationProtocol.isMlsCapable(): Boolean =
    this is CallConversationProtocol.Mls

@ExperimentalCallingRuntimeApi
public fun interface CallingPayloadExtractor<in Event> {
    /** Returns every calling payload in stable wire order, including MLS multi-message batches. */
    public fun extract(event: Event): List<IncomingCallingPayload>
}

/** Required event handler that forwards only decrypted calling payloads into the AVS runtime. */
@ExperimentalCallingRuntimeApi
public class CallingEventHandler<Event>(
    private val extractor: CallingPayloadExtractor<Event>,
    private val runtime: CallingRuntime,
    private val idempotencyStore: CallingEventIdempotencyStore,
) : EventHandler<Event> {
    override val requirement: EventHandlerRequirement = EventHandlerRequirement.REQUIRED

    // Extraction may include decoding a multi-message MLS batch, so perform it exactly once in handle.
    override fun accepts(event: Event): Boolean = true

    @Suppress("ReturnCount")
    override suspend fun handle(event: Event, context: EventHandlingContext): EventHandlingResult {
        val payloads = extractor.extract(event)
        if (payloads.isEmpty()) return EventHandlingResult.Ignored
        payloads.forEachIndexed { index, payload ->
            val payloadKey = payload.messageId?.let { messageId ->
                "wire:${payload.transportConversationId.domain}:${payload.transportConversationId.value}:" +
                        "${payload.senderUserId.domain}:${payload.senderUserId.value}:${payload.senderClientId}:$messageId"
            } ?: "${context.idempotencyKey.value}#$index"
            when (val handled = idempotencyStore.isHandled(payloadKey)) {
                is CallingEventIdempotencyResult.Failure -> return EventHandlingResult.Failed(handled.description, handled.cause)
                is CallingEventIdempotencyResult.Found -> if (handled.handled) return@forEachIndexed
            }
            when (val result = runtime.receive(payload, payloadKey)) {
                CallingResult.Success -> when (val marked = idempotencyStore.markHandled(payloadKey)) {
                    is CallingEventIdempotencyResult.Failure -> return EventHandlingResult.Failed(marked.description, marked.cause)
                    is CallingEventIdempotencyResult.Found -> Unit
                }
                is CallingResult.Failure -> return EventHandlingResult.Failed(result.failure.toString())
            }
        }
        return EventHandlingResult.Handled
    }
}
