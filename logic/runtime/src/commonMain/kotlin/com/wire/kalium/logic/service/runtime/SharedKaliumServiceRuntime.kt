@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.event.processing.ExperimentalEventProcessingApi::class,
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.service.runtime

import com.wire.kalium.calling.runtime.ActiveCall
import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.CallingRuntime
import com.wire.kalium.event.processing.EventProcessingFailure
import com.wire.kalium.event.processing.EventProcessingOutcome
import com.wire.kalium.event.processing.EventProcessor
import com.wire.kalium.events.EventSource
import com.wire.kalium.events.EventSourceResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.KaliumServiceRuntime
import com.wire.kalium.logic.service.api.ServiceConfig
import com.wire.kalium.logic.service.api.ServiceCalling
import com.wire.kalium.logic.service.api.ServiceCryptoRuntime
import com.wire.kalium.logic.service.api.ServiceFailure
import com.wire.kalium.logic.service.api.ServiceObserver
import com.wire.kalium.logic.service.api.ServiceResult
import com.wire.kalium.logic.service.api.ServiceRuntimeState
import com.wire.kalium.logic.service.api.ServiceSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@ExperimentalKaliumServiceApi
@Suppress("LongParameterList", "TooManyFunctions")
public class SharedKaliumServiceRuntime<RawEvent, DecodedEvent, DecryptedEvent>(
    private val config: ServiceConfig,
    private val sessionManager: ServiceSessionManager,
    private val cryptoRuntime: ServiceCryptoRuntime,
    private val eventSource: EventSource<RawEvent>,
    private val eventProcessor: EventProcessor<RawEvent, DecodedEvent, DecryptedEvent>,
    private val callingRuntime: CallingRuntime,
    private val observer: ServiceObserver,
) : KaliumServiceRuntime {
    private val lifecycleMutex = Mutex()
    private val ownedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(ServiceRuntimeState.CREATED)
    private var eventJob: Job? = null
    private var sessionStarted: Boolean = false
    private var cryptoStarted: Boolean = false
    private var sessionClosed: Boolean = false
    private var cryptoClosed: Boolean = false
    private var eventSourceClosed: Boolean = false
    private var callingClosed: Boolean = false

    override val state: StateFlow<ServiceRuntimeState> = mutableState.asStateFlow()
    override val calls: ServiceCalling = RuntimeServiceCalling(state, callingRuntime)

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override suspend fun start(): ServiceResult = lifecycleMutex.withLock {
        when (mutableState.value) {
            ServiceRuntimeState.CREATED -> Unit
            ServiceRuntimeState.CLOSED,
            ServiceRuntimeState.CLOSING -> return@withLock ServiceResult.Failure(ServiceFailure.Closed)
            else -> return@withLock ServiceResult.Failure(ServiceFailure.AlreadyStarted)
        }
        transition(ServiceRuntimeState.STARTING)
        try {
            sessionStarted = true
            when (val result = startSession()) {
                is ServiceResult.Failure -> return@withLock failStartup(result.failure)
                ServiceResult.Success -> Unit
            }
            cryptoStarted = true
            when (val result = startCrypto()) {
                is ServiceResult.Failure -> return@withLock failStartup(result.failure)
                ServiceResult.Success -> Unit
            }
            when (val result = startCalling()) {
                is CallingResult.Failure -> return@withLock failStartup(ServiceFailure.Calling(result.failure.toString()))
                CallingResult.Success -> Unit
            }

            val eventReady = CompletableDeferred<ServiceResult>()
            val runtimeReady = CompletableDeferred<Unit>()
            eventJob = ownedScope.launch(start = CoroutineStart.LAZY) {
                try {
                    eventProcessor.process().collect { outcome ->
                        notifyEvent(outcome)
                        when (outcome) {
                            is EventProcessingOutcome.Ready -> if (eventReady.complete(ServiceResult.Success)) {
                                runtimeReady.await()
                            }
                            is EventProcessingOutcome.Failed -> {
                                val failure = ServiceFailure.Events(outcome.failure.toString())
                                if (!eventReady.complete(ServiceResult.Failure(failure))) {
                                    failFromEventLoop(outcome.failure)
                                }
                            }
                            else -> Unit
                        }
                    }
                    val processingFailure = EventProcessingFailure.Source("Event processing completed unexpectedly")
                    if (!eventReady.complete(ServiceResult.Failure(ServiceFailure.Events(processingFailure.toString())))) {
                        failFromEventLoop(processingFailure)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    val processingFailure = EventProcessingFailure.Source("Event processing stopped", failure)
                    notifyEvent(EventProcessingOutcome.Failed(null, processingFailure))
                    if (!eventReady.complete(ServiceResult.Failure(ServiceFailure.Events(processingFailure.toString())))) {
                        failFromEventLoop(processingFailure)
                    }
                }
            }
            eventJob?.start()
            when (val result = eventReady.await()) {
                is ServiceResult.Failure -> {
                    runtimeReady.complete(Unit)
                    return@withLock failStartup(result.failure)
                }
                ServiceResult.Success -> Unit
            }
            transition(ServiceRuntimeState.READY)
            runtimeReady.complete(Unit)
            ServiceResult.Success
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { cleanupOwnedResources() }
            transition(ServiceRuntimeState.FAILED)
            throw cancellation
        }
    }

    override suspend fun close(): ServiceResult = lifecycleMutex.withLock {
        if (mutableState.value == ServiceRuntimeState.CLOSED) return@withLock ServiceResult.Success
        transition(ServiceRuntimeState.CLOSING)
        val failure = withContext(NonCancellable) { cleanupOwnedResources() }
        if (failure == null) {
            transition(ServiceRuntimeState.CLOSED)
            ServiceResult.Success
        } else {
            transition(ServiceRuntimeState.FAILED)
            notifyFailure(failure)
            ServiceResult.Failure(failure)
        }
    }

    private suspend fun fail(failure: ServiceFailure): ServiceResult.Failure {
        transition(ServiceRuntimeState.FAILED)
        notifyFailure(failure)
        return ServiceResult.Failure(failure)
    }

    private suspend fun failStartup(failure: ServiceFailure): ServiceResult.Failure {
        val cleanupFailure = withContext(NonCancellable) { cleanupOwnedResources() }
        cleanupFailure?.let(::notifyFailure)
        return fail(failure)
    }

    private fun transition(state: ServiceRuntimeState) {
        mutableState.value = state
        try {
            observer.onStateChanged(state)
        } catch (_: Throwable) {
            // Observability must not break protocol lifecycle.
        }
    }

    private fun failFromEventLoop(failure: EventProcessingFailure) {
        if (mutableState.compareAndSet(ServiceRuntimeState.READY, ServiceRuntimeState.FAILED)) {
            val serviceFailure = ServiceFailure.Events(failure.toString())
            notifyStateChanged(ServiceRuntimeState.FAILED)
            notifyFailure(serviceFailure)
        }
    }

    private fun notifyStateChanged(state: ServiceRuntimeState) {
        try {
            observer.onStateChanged(state)
        } catch (_: Throwable) {
            // Observability must not break protocol lifecycle.
        }
    }

    private fun notifyEvent(outcome: EventProcessingOutcome) {
        try {
            observer.onEvent(outcome)
        } catch (_: Throwable) {
            // Observability must not break event acknowledgement.
        }
    }

    private fun notifyFailure(failure: ServiceFailure) {
        try {
            observer.onFailure(failure)
        } catch (_: Throwable) {
            // Observability must not break protocol lifecycle.
        }
    }

    private suspend fun startSession(): ServiceResult = try {
        sessionManager.start(config.identity)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        ServiceResult.Failure(ServiceFailure.Session("Session startup threw an exception", failure))
    }

    private suspend fun startCrypto(): ServiceResult = try {
        cryptoRuntime.start(config.identity)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        ServiceResult.Failure(ServiceFailure.Crypto("Crypto startup threw an exception", failure))
    }

    private suspend fun startCalling(): CallingResult = try {
        callingRuntime.start()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        CallingResult.Failure(CallingFailure.Avs("AVS startup threw an exception", failure))
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun cleanupOwnedResources(): ServiceFailure? {
        var firstFailure: ServiceFailure? = null
        eventJob?.cancel()
        if (!eventSourceClosed) {
            val failure = closeEventSource()
            if (failure == null) eventSourceClosed = true else firstFailure = failure
        }
        eventJob?.let { job ->
            val stopped = withTimeoutOrNull(config.shutdownTimeoutMillis) {
                job.join()
                true
            } ?: false
            if (stopped) {
                eventJob = null
            } else if (firstFailure == null) {
                firstFailure = ServiceFailure.Cleanup("Event processor did not stop before the shutdown timeout")
            }
        }
        if (!callingClosed) {
            val failure = closeCalls()
            if (failure == null) callingClosed = true else if (firstFailure == null) firstFailure = failure
        }
        val dependantsClosed = eventSourceClosed && eventJob == null && callingClosed
        if (dependantsClosed && !cryptoClosed) {
            val failure = closeCrypto()
            if (failure == null) {
                cryptoStarted = false
                cryptoClosed = true
            } else if (firstFailure == null) {
                firstFailure = failure
            }
        }
        if (dependantsClosed && cryptoClosed && !sessionClosed) {
            val failure = closeSession()
            if (failure == null) {
                sessionStarted = false
                sessionClosed = true
            } else if (firstFailure == null) {
                firstFailure = failure
            }
        }
        ownedScope.cancel()
        return firstFailure
    }

    private suspend fun closeEventSource(): ServiceFailure? = try {
        when (val result = eventSource.close()) {
            is EventSourceResult.Failure -> ServiceFailure.Events(result.description, result.cause)
            EventSourceResult.Success -> null
        }
    } catch (failure: Throwable) {
        ServiceFailure.Cleanup("Event source cleanup threw an exception", failure)
    }

    private suspend fun closeCalls(): ServiceFailure? = try {
        when (val result = callingRuntime.close()) {
            is CallingResult.Failure -> ServiceFailure.Calling(result.failure.toString())
            CallingResult.Success -> null
        }
    } catch (failure: Throwable) {
        ServiceFailure.Cleanup("Calling cleanup threw an exception", failure)
    }

    private suspend fun closeCrypto(): ServiceFailure? = try {
        when (val result = cryptoRuntime.close()) {
            is ServiceResult.Failure -> result.failure
            ServiceResult.Success -> null
        }
    } catch (failure: Throwable) {
        ServiceFailure.Cleanup("Crypto cleanup threw an exception", failure)
    }

    private suspend fun closeSession(): ServiceFailure? = try {
        when (val result = sessionManager.close()) {
            is ServiceResult.Failure -> result.failure
            ServiceResult.Success -> null
        }
    } catch (failure: Throwable) {
        ServiceFailure.Cleanup("Session cleanup threw an exception", failure)
    }
}

private class RuntimeServiceCalling(
    private val state: StateFlow<ServiceRuntimeState>,
    private val delegate: CallingRuntime,
) : ServiceCalling {
    override fun observeActiveCalls(): Flow<List<ActiveCall>> = delegate.observeActiveCalls()

    override suspend fun join(conversationId: ConversationId): CallingResult = when (state.value) {
        ServiceRuntimeState.READY -> delegate.join(conversationId)
        ServiceRuntimeState.CLOSING,
        ServiceRuntimeState.CLOSED -> CallingResult.Failure(CallingFailure.RuntimeClosed)
        else -> CallingResult.Failure(CallingFailure.RuntimeNotReady)
    }

    override suspend fun leave(conversationId: ConversationId): CallingResult = when (state.value) {
        ServiceRuntimeState.READY,
        ServiceRuntimeState.FAILED -> delegate.leave(conversationId)
        ServiceRuntimeState.CLOSING,
        ServiceRuntimeState.CLOSED -> CallingResult.Failure(CallingFailure.RuntimeClosed)
        else -> CallingResult.Failure(CallingFailure.RuntimeNotReady)
    }

    override suspend fun recordAudio(path: String): CallingResult = when (state.value) {
        ServiceRuntimeState.READY -> delegate.recordAudio(path)
        ServiceRuntimeState.CLOSING,
        ServiceRuntimeState.CLOSED -> CallingResult.Failure(CallingFailure.RuntimeClosed)
        else -> CallingResult.Failure(CallingFailure.RuntimeNotReady)
    }
}
