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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
            // The execution context covers the whole call, including reading the response body.
            val call = context.executionContext
            coroutineScope {
                var failed = false
                val failer = launch(start = CoroutineStart.UNDISPATCHED) {
                    failing.first { it > 0 }
                    failed = true
                    call.cancel(CancellationException(FAILED_MESSAGE))
                }
                try {
                    proceed()
                } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                    ensureActive()
                    if (failed) throw IOException(FAILED_MESSAGE, failure)
                    throw failure
                } finally {
                    failer.cancel()
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
