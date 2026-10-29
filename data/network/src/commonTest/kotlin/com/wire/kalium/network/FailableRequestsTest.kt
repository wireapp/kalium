/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.network

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class FailableRequestsTest : ApiTest() {

    @Test
    fun callerTimeoutEndsARequestInsideANonCancellableCryptoCallback() = runTest {
        val entered = CompletableDeferred<Unit>()
        val provider = provider(MockEngine {
            entered.complete(Unit)
            awaitCancellation()
        })
        var failure: Throwable? = null
        val request = launch {
            try {
                withTimeout(1_000) {
                    val caller = RequestCallerCancellation.capture()
                    withContext(caller + NonCancellable) { provider.networkClient.httpClient.get("self") }
                }
            } catch (cancelled: TimeoutCancellationException) {
                failure = cancelled
            }
        }
        try {
            entered.await()
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(request.isCompleted, "A native callback must finish when the original caller times out")
            assertIs<TimeoutCancellationException>(failure)
        } finally {
            // Also drains the old broken implementation when proving the regression.
            if (!request.isCompleted) provider.failRequests { request.join() }
            provider.close()
        }
    }

    @Test
    fun callerTimeoutEndsAPluginWaitInsideANonCancellableCryptoCallback() = runTest {
        val entered = CompletableDeferred<Unit>()
        val provider = provider(MockEngine { respondOk() })
        val client = provider.networkClient.httpClient
        client.sendPipeline.intercept(HttpSendPipeline.Before) {
            entered.complete(Unit)
            awaitCancellation()
        }
        val request = launch {
            try {
                withTimeout(1_000) {
                    val caller = RequestCallerCancellation.capture()
                    withContext(caller + NonCancellable) { client.get("self") }
                }
            } catch (_: TimeoutCancellationException) {
                // The original timeout remains cancellation, even in the native callback context.
            }
        }
        try {
            entered.await()
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(request.isCompleted, "Caller cancellation must end waits before the HTTP engine too")
        } finally {
            if (!request.isCompleted) provider.failRequests { request.join() }
            provider.close()
        }
    }

    @Test
    fun oneCallersTimeoutLeavesTheAccountsOtherRequestsRunning() = runTest {
        val statusEntered = CompletableDeferred<Unit>()
        val unrelatedEntered = CompletableDeferred<Unit>()
        val finishUnrelated = CompletableDeferred<Unit>()
        val provider = provider(MockEngine { request ->
            if (request.url.encodedPath.endsWith("status")) {
                statusEntered.complete(Unit)
                awaitCancellation()
            } else {
                unrelatedEntered.complete(Unit)
                finishUnrelated.await()
                respondOk()
            }
        })
        val unrelated = async { provider.networkClient.httpClient.get("unrelated") }
        val status = launch {
            try {
                withTimeout(1_000) {
                    val caller = RequestCallerCancellation.capture()
                    withContext(caller + NonCancellable) { provider.networkClient.httpClient.get("status") }
                }
            } catch (_: TimeoutCancellationException) {
                // Expected for this one operation only.
            }
        }
        try {
            statusEntered.await()
            unrelatedEntered.await()
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(status.isCompleted)
            assertFalse(unrelated.isCompleted)
            assertFalse(unrelated.isCancelled)
            finishUnrelated.complete(Unit)
            assertEquals(HttpStatusCode.OK, unrelated.await().status)
        } finally {
            finishUnrelated.complete(Unit)
            unrelated.cancelAndJoin()
            if (!status.isCompleted) provider.failRequests { status.join() }
            provider.close()
        }
    }

    @Test
    fun failingRequestsEndsANonCancellableRequestInFlightAsNoNetwork() = runTest {
        val entered = CompletableDeferred<Unit>()
        val provider = provider(MockEngine {
            entered.complete(Unit)
            awaitCancellation()
        })
        // A message send runs its request inside a non-cancellable crypto transaction.
        val request = async {
            withContext(NonCancellable) { runCatching { provider.networkClient.httpClient.get("self") } }
        }
        entered.await()
        val failing = launch { provider.failRequests { awaitCancellation() } }
        assertIs<IOException>(request.await().exceptionOrNull())
        failing.cancelAndJoin()
        provider.close()
    }

    @Test
    fun failingRequestsEndsAWaitOutsideTheEngine() = runTest {
        val waiting = CompletableDeferred<Unit>()
        val provider = provider(MockEngine { respondOk() })
        val client = provider.networkClient.httpClient
        // Stands in for a plugin wait, such as a token refresh that the account owner no longer admits.
        client.sendPipeline.intercept(HttpSendPipeline.Before) {
            waiting.complete(Unit)
            awaitCancellation()
        }
        val request = async {
            withContext(NonCancellable) { runCatching { client.get("self") } }
        }
        waiting.await()
        val failing = launch { provider.failRequests { awaitCancellation() } }
        assertIs<IOException>(request.await().exceptionOrNull())
        failing.cancelAndJoin()
        provider.close()
    }

    @Test
    fun aBackendFailureStaysThatFailure() = runTest {
        val provider = provider(MockEngine { throw IOException("connection lost") })
        val failure = assertFailsWith<IOException> { provider.networkClient.httpClient.get("self") }
        assertEquals("connection lost", failure.message)
        provider.close()
    }

    @Test
    fun requestsFailWithoutReachingTheBackendUntilFailingEnds() = runTest {
        var calls = 0
        val provider = provider(MockEngine {
            calls++
            respondOk()
        })
        provider.failRequests {
            assertFailsWith<IOException> { provider.networkClient.httpClient.get("self") }
            assertFailsWith<IOException> { provider.networkClientWithoutCompression.httpClient.get("self") }
        }
        assertEquals(0, calls)
        assertEquals(HttpStatusCode.OK, provider.networkClient.httpClient.get("self").status)
        assertEquals(1, calls)
        provider.close()
    }

    @Test
    fun cancellingTheCallerStaysACancellation() = runTest {
        val entered = CompletableDeferred<Unit>()
        val provider = provider(MockEngine {
            entered.complete(Unit)
            awaitCancellation()
        })
        var failure: Throwable? = null
        val request = launch {
            try {
                provider.networkClient.httpClient.get("self")
            } catch (cancelled: CancellationException) {
                failure = cancelled
                throw cancelled
            }
        }
        entered.await()
        request.cancelAndJoin()
        assertIs<CancellationException>(failure)
        provider.close()
    }

    @Test
    fun aCancelledCallerStaysCancelledWhileRequestsFail() = runTest {
        val provider = provider(MockEngine { respondOk() })
        var failure: Throwable? = null
        provider.failRequests {
            launch {
                coroutineContext.job.cancel()
                failure = runCatching { provider.networkClient.httpClient.get("self") }.exceptionOrNull()
            }.join()
        }
        assertIs<CancellationException>(failure)
        provider.close()
    }

    private fun provider(engine: HttpClientEngine) = AuthenticatedHttpClientProviderImpl(
        sessionManager = TEST_SESSION_MANAGER,
        accessTokenApi = { AccessTokenApiV0(it) },
        engine = engine,
        ownsEngine = true,
        webSocketSessionProvider = null,
        kaliumLogger = kaliumLogger,
    )
}
