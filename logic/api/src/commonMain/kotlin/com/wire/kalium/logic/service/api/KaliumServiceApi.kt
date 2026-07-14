@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.event.processing.ExperimentalEventProcessingApi::class,
)

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

package com.wire.kalium.logic.service.api

import com.wire.kalium.calling.runtime.ActiveCall
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.event.processing.EventProcessingOutcome
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "The Kalium service API is experimental until calling-team confirmation.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalKaliumServiceApi

@ExperimentalKaliumServiceApi
public data class ServiceIdentity(public val userId: UserId, public val clientId: String, public val backendDomain: String) {
    init {
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(backendDomain.isNotBlank()) { "backendDomain must not be blank" }
    }
}

@ExperimentalKaliumServiceApi
public data class ServiceConfig(
    public val identity: ServiceIdentity,
    public val maxConcurrentCalls: Int = 1,
    public val shutdownTimeoutMillis: Long = 30_000,
) {
    init {
        require(maxConcurrentCalls > 0) { "maxConcurrentCalls must be positive" }
        require(shutdownTimeoutMillis > 0) { "shutdownTimeoutMillis must be positive" }
    }
}

@ExperimentalKaliumServiceApi
public enum class ServiceRuntimeState {
    CREATED,
    STARTING,
    READY,
    FAILED,
    CLOSING,
    CLOSED,
}

@ExperimentalKaliumServiceApi
public sealed interface ServiceFailure {
    public data object AlreadyStarted : ServiceFailure

    public data object Closed : ServiceFailure

    public data class Session(public val description: String, public val cause: Throwable? = null) : ServiceFailure

    public data class Crypto(public val description: String, public val cause: Throwable? = null) : ServiceFailure

    public data class Events(public val description: String, public val cause: Throwable? = null) : ServiceFailure

    public data class Calling(public val description: String, public val cause: Throwable? = null) : ServiceFailure

    public data class Cleanup(public val description: String, public val cause: Throwable? = null) : ServiceFailure
}

@ExperimentalKaliumServiceApi
public sealed interface ServiceResult {
    public data object Success : ServiceResult

    public data class Failure(public val failure: ServiceFailure) : ServiceResult
}

@ExperimentalKaliumServiceApi
public interface ServiceSessionManager {
    /**
     * Restores durable credentials and Wire client identity, then makes authenticated HTTP and
     * WebSocket access ready for exactly [identity].
     */
    public suspend fun start(identity: ServiceIdentity): ServiceResult

    /** Closes authenticated transports. Implementations must be idempotent. */
    public suspend fun close(): ServiceResult
}

@ExperimentalKaliumServiceApi
public interface ServiceCryptoRuntime {
    /**
     * Opens durable Proteus/CoreCrypto/MLS state for exactly [identity]. Implementations must fail
     * rather than silently substituting an ephemeral or no-op crypto store.
     */
    public suspend fun start(identity: ServiceIdentity): ServiceResult

    /** Flushes and closes crypto state. Implementations must be idempotent. */
    public suspend fun close(): ServiceResult
}

@ExperimentalKaliumServiceApi
public interface ServiceObserver {
    public fun onStateChanged(state: ServiceRuntimeState) {}

    public fun onEvent(outcome: EventProcessingOutcome) {}

    public fun onFailure(failure: ServiceFailure) {}
}

/** Calling commands exposed after the service reaches [ServiceRuntimeState.READY]. */
@ExperimentalKaliumServiceApi
public interface ServiceCalling {
    public fun observeActiveCalls(): Flow<List<ActiveCall>>

    public suspend fun join(conversationId: ConversationId): CallingResult

    public suspend fun leave(conversationId: ConversationId): CallingResult
}

@ExperimentalKaliumServiceApi
public interface KaliumServiceRuntime {
    public val state: StateFlow<ServiceRuntimeState>

    public val calls: ServiceCalling

    public suspend fun start(): ServiceResult

    public suspend fun close(): ServiceResult
}
