/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.cryptography

import com.wire.kalium.util.AccountResourceAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoreCryptoCallbackContextTest {
    @Test
    fun allPkiCallbacksDrainWhileUnrelatedOperationsRemainBlocked() = runTest {
        val access = AccountResourceAccess()
        val callbacks = CoreCryptoCallbackContext(access)
        val proceed = CompletableDeferred<Unit>()
        val called = mutableListOf<String>()
        val hooks = callbacks.pki(Hooks { name -> access.withAccess { called += name; name } }, 0)
        val worker = backgroundScope.async {
            access.withAccess {
                callbacks.operation(0) {
                    proceed.await()
                    backgroundScope.async {
                        hooks.httpRequest(PkiHttpMethod.GET, "https://pki.test", emptyList(), byteArrayOf())
                        hooks.authenticate("idp", "key", "audience")
                        hooks.getBackendNonce()
                        hooks.fetchBackendAccessToken("dpop")
                    }.await()
                }
            }
        }
        runCurrent()
        val pause = backgroundScope.launch { access.pause {} }
        runCurrent()
        val unrelated = backgroundScope.async { access.withAccess { "unrelated" } }
        proceed.complete(Unit)
        runCurrent()
        assertTrue(worker.isCompleted)
        assertTrue(pause.isCompleted)
        assertEquals(listOf("http", "authenticate", "nonce", "token"), called)
        assertFalse(unrelated.isCompleted)
        access.resume {}
        assertEquals("unrelated", unrelated.await())
    }

    @Test
    fun queuedOperationCannotReplaceActiveCallbackContextAndReentryDoesNotLockAgain() = runTest {
        val access = AccountResourceAccess()
        val callbacks = CoreCryptoCallbackContext(access)
        val proceed = CompletableDeferred<Unit>()
        val hooks = callbacks.pki(Hooks {
            callbacks.operation(0) {
                access.withAccess { checkNotNull(currentCoroutineContext()[CoroutineName]).name }
            }
        }, 0)
        val first = backgroundScope.async(CoroutineName("first")) {
            access.withAccess {
                callbacks.operation(0) {
                    proceed.await()
                    backgroundScope.async { hooks.getBackendNonce() }.await()
                }
            }
        }
        runCurrent()
        val second = backgroundScope.async(CoroutineName("second")) {
            access.withAccess {
                callbacks.operation(0) { backgroundScope.async { hooks.getBackendNonce() }.await() }
            }
        }
        runCurrent()
        assertFalse(second.isCompleted)
        proceed.complete(Unit)
        assertEquals("first", first.await())
        assertEquals("second", second.await())
    }

    @Test
    fun callbacksCannotUseExpiredOrPreviousGenerationAdmission() = runTest {
        val access = AccountResourceAccess()
        val callbacks = CoreCryptoCallbackContext(access)
        var calls = 0
        val oldHooks = callbacks.pki(Hooks { calls++; "nonce" }, 0)
        access.withAccess { callbacks.operation(0) { assertEquals("nonce", oldHooks.getBackendNonce()) } }
        assertFailsWith<IllegalStateException> { oldHooks.getBackendNonce() }
        access.pause {}
        assertFailsWith<IllegalStateException> { oldHooks.getBackendNonce() }
        access.resume {}
        access.withAccess {
            callbacks.operation(1) { assertFailsWith<IllegalStateException> { oldHooks.getBackendNonce() } }
        }
        assertEquals(1, calls)
    }

    @Test
    fun cancellationRevokesCallbackAdmissionAndAllowsSuspension() = runTest {
        val access = AccountResourceAccess()
        val callbacks = CoreCryptoCallbackContext(access)
        val hooks = callbacks.pki(Hooks { "nonce" }, 0)
        val worker = backgroundScope.launch { access.withAccess { callbacks.operation(0) { awaitCancellation() } } }
        runCurrent()
        worker.cancelAndJoin()
        access.pause {}
        assertFailsWith<IllegalStateException> { hooks.getBackendNonce() }
        access.resume {}
        access.withAccess { callbacks.operation(0) { assertEquals("nonce", hooks.getBackendNonce()) } }
    }

    @Test
    fun failingCallbackDoesNotLeaveAdmissionOrSerializationHeld() = runTest {
        val access = AccountResourceAccess()
        val callbacks = CoreCryptoCallbackContext(access)
        val hooks = callbacks.pki(Hooks { error("callback failed") }, 0)
        assertFailsWith<IllegalStateException> {
            access.withAccess {
                callbacks.operation(0) { backgroundScope.async { runCatching { hooks.getBackendNonce() } }.await().getOrThrow() }
            }
        }
        access.pause {}
        access.resume {}
        assertEquals("next", access.withAccess { callbacks.operation(0) { "next" } })
    }

    private class Hooks(private val invoke: suspend (String) -> String) : PkiEnvironmentHooks {
        override suspend fun httpRequest(
            method: PkiHttpMethod,
            url: String,
            headers: List<PkiHttpHeader>,
            body: ByteArray,
        ): PkiHttpResponse {
            invoke("http")
            return PkiHttpResponse(200u, emptyList(), byteArrayOf())
        }
        override suspend fun authenticate(idp: String, keyAuth: String, acmeAud: String) = invoke("authenticate")
        override suspend fun getBackendNonce() = invoke("nonce")
        override suspend fun fetchBackendAccessToken(dpop: String) = invoke("token")
    }
}
