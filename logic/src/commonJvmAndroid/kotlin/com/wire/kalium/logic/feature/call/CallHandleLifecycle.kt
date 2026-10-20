/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.LifecycleCalling
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.types.Handle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns a published native handle independently of the coroutine waiting for readiness. */
internal class CallHandleLifecycle(
    private val calling: LifecycleCalling,
    private val readyTimeoutMillis: Long = DEFAULT_READY_TIMEOUT_MILLIS,
    private val releaseTimeoutMillis: Long = DEFAULT_RELEASE_TIMEOUT_MILLIS
) {
    private val nativeAccess = Mutex()
    private var handle: Handle? = null

    @Volatile
    private var stopping = false

    @Suppress("TooGenericExceptionCaught") // Native ownership must be retained for every initialization failure.
    suspend fun create(createHandle: (ReadyHandler) -> Handle): Handle {
        val ready = CompletableDeferred<Unit>()
        val created = nativeAccess.withLock {
            check(!stopping) { "Calling handle is stopping" }
            check(handle == null) { "Calling handle is already created" }
            createHandle(
                ReadyHandler { _, _ ->
                    // Signal readiness; Kotlin setup executes after the native callback returns.
                    ready.complete(Unit)
                    Unit
                }
            ).also {
                check(it.toLong() != 0L) { "wcall_create returned an invalid handle" }
                handle = it
            }
        }
        try {
            withTimeout(readyTimeoutMillis) { ready.await() }
            currentCoroutineContext().ensureActive()
            return created
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                try {
                    release()
                } catch (releaseFailure: Throwable) {
                    failure.addSuppressed(releaseFailure)
                }
            }
            throw failure
        }
    }

    suspend fun <T> withHandle(expected: Handle, action: suspend () -> T): T = nativeAccess.withLock {
        currentCoroutineContext().ensureActive()
        check(!stopping && handle?.toLong() == expected.toLong()) { "Calling handle is stopping or released" }
        action()
    }

    /** A failed release deliberately retains ownership and allows another teardown invocation to retry. */
    suspend fun release() {
        stopAdmission()
        withTimeout(releaseTimeoutMillis) {
            nativeAccess.withLock {
                handle?.let {
                    calling.releaseHandle(it)
                    handle = null
                }
            }
        }
    }

    fun stopAdmission() {
        stopping = true
    }

    internal companion object {
        private const val DEFAULT_READY_TIMEOUT_MILLIS = 15_000L
        private const val DEFAULT_RELEASE_TIMEOUT_MILLIS = 10_000L
    }
}
