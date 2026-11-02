/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.network

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.io.IOException

/**
 * Fails the requests of the clients it is installed on, in flight and new, while [failRequests] runs.
 * A failed request throws an [IOException], which callers treat as no network, so it also ends a crypto
 * transaction that waits on it. The caller's own cancellation still wins.
 */
internal class FailableRequests {
    private val failing = MutableStateFlow(0)

    fun install(client: HttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            val caller = currentCoroutineContext()
            val originalCaller = caller[RequestCallerCancellation]
            originalCaller?.ensureActive()
            // Fail before plugins can wait, for example for a token the account owner no longer admits.
            if (failing.value > 0) {
                caller.ensureActive()
                throw IOException(FAILED_MESSAGE)
            }
            supervisorScope {
                // A child, so failing also ends waits outside the engine, such as a token refresh, even when the
                // caller is non-cancellable. Cancelling it cancels the engine call too.
                val call = async { proceed() }
                // Native crypto callbacks are non-cancellable, but their initiating caller still owns this request.
                val callerCancellation = originalCaller?.cancelRequestWithCaller(call)
                var failed = false
                val failer = launch(start = CoroutineStart.UNDISPATCHED) {
                    failing.first { it > 0 }
                    failed = true
                    call.cancel(CancellationException(FAILED_MESSAGE))
                }
                try {
                    call.await()
                } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                    caller.ensureActive()
                    originalCaller?.ensureActive()
                    throw if (failed) IOException(FAILED_MESSAGE, failure) else failure
                } finally {
                    failer.cancel()
                    callerCancellation?.cancel()
                }
            }
        }
    }

    suspend fun <T> failRequests(block: suspend () -> T): T {
        failing.update { it + 1 }
        return try {
            block()
        } finally {
            failing.update { it - 1 }
        }
    }

    private companion object {
        const val FAILED_MESSAGE = "The account's requests were failed on purpose"
    }
}
