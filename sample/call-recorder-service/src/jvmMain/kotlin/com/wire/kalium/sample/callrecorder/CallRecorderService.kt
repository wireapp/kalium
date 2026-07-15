@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
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
 */

package com.wire.kalium.sample.callrecorder

import com.wire.kalium.calling.runtime.CallEventSink
import com.wire.kalium.calling.runtime.CallLifecycleEvent
import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.service.WireKaliumService
import com.wire.kalium.logic.service.WireKaliumServiceConfig
import com.wire.kalium.logic.service.api.KaliumServiceRuntime
import com.wire.kalium.logic.service.api.ServiceCalling
import com.wire.kalium.logic.service.api.ServiceFailure
import com.wire.kalium.logic.service.api.ServiceObserver
import com.wire.kalium.logic.service.api.ServiceResult
import com.wire.kalium.logic.service.api.ServiceRuntimeState
import java.nio.file.Path
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Inert until [start] and safe to close more than once. */
internal class CallRecorderService private constructor(
    private val runtime: KaliumServiceRuntime,
    private val eventSink: AutoAnswerRecorder,
) {
    private val closeMutex = Mutex()
    private var closed = false

    suspend fun start(): ServiceResult {
        SafeLog.info("Starting call-recorder runtime")
        return runtime.start().also { result ->
            when (result) {
                ServiceResult.Success -> SafeLog.info("Call-recorder runtime is ready")
                is ServiceResult.Failure -> SafeLog.error("Call-recorder startup failed", result.failure)
            }
        }
    }

    suspend fun awaitTermination(): ServiceRuntimeState = runtime.state
        .filter { it == ServiceRuntimeState.FAILED || it == ServiceRuntimeState.CLOSED }
        .first()

    suspend fun close(): ServiceResult = closeMutex.withLock {
        if (closed) return@withLock ServiceResult.Success
        SafeLog.info("Closing call-recorder runtime")
        val runtimeResult = try {
            runtime.close()
        } catch (failure: Throwable) {
            ServiceResult.Failure(ServiceFailure.Cleanup("Runtime close threw", failure))
        }
        // A failed runtime close may leave AVS writing. Keep raw PCM partial instead of finalizing it.
        val recorderResult = if (runtimeResult == ServiceResult.Success) eventSink.close() else CallingResult.Success
        closed = runtimeResult == ServiceResult.Success && recorderResult == CallingResult.Success
        when {
            runtimeResult is ServiceResult.Failure -> runtimeResult.also {
                SafeLog.error("Call-recorder runtime close failed", it.failure)
            }
            recorderResult is CallingResult.Failure -> ServiceResult.Failure(
                ServiceFailure.Cleanup("Recording finalization failed: ${recorderResult.failure.safeName()}"),
            ).also { SafeLog.error("Recording finalization failed", it.failure) }
            else -> ServiceResult.Success.also { SafeLog.info("Call-recorder runtime is closed") }
        }
    }

    companion object {
        fun create(config: WireKaliumServiceConfig, recordingsDirectory: Path): CallRecorderService {
            require(config.service.maxConcurrentCalls == 1) {
                "AVS recording is process-global; call-recorder maxConcurrentCalls must be 1"
            }
            val sink = AutoAnswerRecorder(recordingsDirectory)
            val runtime = WireKaliumService.create(config.copy(callEventSink = sink), SafeServiceObserver)
            sink.bind(runtime.calls)
            return CallRecorderService(runtime, sink)
        }
    }
}

private class AutoAnswerRecorder(private val outputDirectory: Path) : CallEventSink {
    private val mutex = Mutex()
    private lateinit var calls: ServiceCalling
    private var active: ActiveRecording? = null
    private var closed = false

    fun bind(calls: ServiceCalling) {
        check(!this::calls.isInitialized) { "Calling runtime is already bound" }
        this.calls = calls
    }

    override suspend fun emit(event: CallLifecycleEvent): CallingResult = when (event) {
        is CallLifecycleEvent.Incoming -> answer(event)
        is CallLifecycleEvent.Closed -> finishIfOwned(event.conversationId, "Call ended")
        is CallLifecycleEvent.Missed -> finishIfOwned(event.conversationId, "Call was missed")
        is CallLifecycleEvent.Failed -> finishIfTerminalFailure(event)
        else -> {
            logLifecycle(event)
            CallingResult.Success
        }
    }

    suspend fun close(): CallingResult = mutex.withLock {
        if (closed) return@withLock CallingResult.Success
        closed = true
        finishLocked("Process shutdown")
    }

    @Suppress("ReturnCount")
    private suspend fun answer(event: CallLifecycleEvent.Incoming): CallingResult {
        val prepared = mutex.withLock {
            if (closed) return@withLock PreparedRecording.Failure(CallingFailure.RuntimeClosed)
            if (!event.call.shouldRing) {
                SafeLog.info("Ignoring an ineligible incoming call")
                return@withLock PreparedRecording.Ineligible
            }
            if (active != null) {
                SafeLog.warn("Cannot answer an incoming call while another recording is active")
                return@withLock PreparedRecording.Failure(CallingFailure.ConcurrencyLimitReached)
            }
            SafeLog.info("Eligible incoming call received; preparing recording")
            val recorder = AvsWavAudioRecorder(calls, outputDirectory)
            when (val started = recorder.start()) {
                is AudioRecordingResult.Started -> {
                    active = ActiveRecording(event.call.conversationId, recorder)
                    PreparedRecording.Ready
                }
                is AudioRecordingResult.Failure -> {
                    recorder.close()
                    SafeLog.error(started.description, started.cause)
                    PreparedRecording.Failure(CallingFailure.State(started.description, started.cause))
                }
                AudioRecordingResult.NoRecording,
                is AudioRecordingResult.Finished -> error("Unexpected recording start result")
            }
        }
        when (prepared) {
            PreparedRecording.Ineligible -> return CallingResult.Success
            is PreparedRecording.Failure -> return CallingResult.Failure(prepared.failure)
            PreparedRecording.Ready -> Unit
        }

        // Do not hold the sink mutex here: join emits lifecycle events back into this sink.
        SafeLog.info("Recording prepared; answering incoming call")
        return calls.join(event.call.conversationId).also { joined ->
            if (joined is CallingResult.Failure) {
                SafeLog.error("Unable to answer incoming call", joined.failure)
                finishIfOwned(event.call.conversationId, "Answer failed")
            }
        }
    }

    private suspend fun finishIfTerminalFailure(event: CallLifecycleEvent.Failed): CallingResult {
        val conversationId = event.conversationId ?: return CallingResult.Success
        val isStillActive = calls.observeActiveCalls().first().any { it.conversationId == conversationId }
        return if (isStillActive) {
            SafeLog.warn("A non-terminal call operation failed; recording remains active")
            CallingResult.Success
        } else {
            finishIfOwned(conversationId, "Call failed")
        }
    }

    private suspend fun finishIfOwned(conversationId: ConversationId, reason: String): CallingResult = mutex.withLock {
        if (active?.conversationId != conversationId) return@withLock CallingResult.Success
        finishLocked(reason)
    }

    private suspend fun finishLocked(reason: String): CallingResult {
        val owned = active ?: return CallingResult.Success
        active = null
        SafeLog.info("$reason; finalizing recording")
        return try {
            when (val result = owned.recorder.finish()) {
                is AudioRecordingResult.Finished -> {
                    SafeLog.info("Recording finalized as ${result.output.fileName}")
                    CallingResult.Success
                }
                AudioRecordingResult.NoRecording -> CallingResult.Success
                is AudioRecordingResult.Failure -> {
                    SafeLog.error(result.description, result.cause)
                    CallingResult.Failure(CallingFailure.State(result.description, result.cause))
                }
                is AudioRecordingResult.Started -> error("Unexpected recording finish result")
            }
        } finally {
            owned.recorder.close()
        }
    }

    private fun logLifecycle(event: CallLifecycleEvent) {
        val transition = when (event) {
            is CallLifecycleEvent.Answered -> "Call answered"
            is CallLifecycleEvent.Established -> "Call established"
            is CallLifecycleEvent.Joined -> "Call joined"
            is CallLifecycleEvent.Joining -> "Call joining"
            is CallLifecycleEvent.Leaving -> "Call leaving"
            is CallLifecycleEvent.Left -> "Call left"
            is CallLifecycleEvent.SignallingReceived -> return
            else -> return
        }
        SafeLog.info(transition)
    }

    private data class ActiveRecording(val conversationId: ConversationId, val recorder: AudioRecorder)

    private sealed interface PreparedRecording {
        data object Ready : PreparedRecording
        data object Ineligible : PreparedRecording
        data class Failure(val failure: CallingFailure) : PreparedRecording
    }
}

private object SafeServiceObserver : ServiceObserver {
    override fun onStateChanged(state: ServiceRuntimeState) {
        SafeLog.info("Runtime state changed to $state")
    }

    override fun onFailure(failure: ServiceFailure) {
        SafeLog.error("Runtime failure", failure)
    }
}

internal object SafeLog {
    fun info(message: String) {
        println("INFO  $message")
    }

    fun warn(message: String) {
        System.err.println("WARN  $message")
    }

    fun error(message: String, failure: Any? = null) {
        val kind = failure?.let { it::class.simpleName } ?: "unknown"
        System.err.println("ERROR $message [$kind]")
    }
}

private fun CallingFailure.safeName(): String = this::class.simpleName ?: "calling failure"
