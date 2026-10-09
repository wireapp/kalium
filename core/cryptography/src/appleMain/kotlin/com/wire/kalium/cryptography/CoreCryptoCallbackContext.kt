/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.cryptography

import com.wire.kalium.util.AccountResourceAccess
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * UniFFI starts awaited native callbacks in unrelated coroutines. Serialize calls to this
 * native owner so a callback can recover the admission of exactly one active operation.
 * Callback re-entry retains that operation, rather than acquiring its mutex a second time.
 */
internal class CoreCryptoCallbackContext(private val access: AccountResourceAccess) {
    private val mutex = Mutex()
    private val active = MutableStateFlow<Operation?>(null)

    suspend fun <T> operation(generation: Long, block: suspend () -> T): T {
        val context = currentCoroutineContext()
        val inherited = context[Operation]
        if (inherited != null && inherited.owner === this && active.value === inherited) return block()
        return mutex.withLock {
            val operation = Operation(this, generation, context)
            active.value = operation
            try {
                withContext(operation) { block() }
            } finally {
                active.value = null
            }
        }
    }

    private suspend fun <T> callback(generation: Long, block: suspend () -> T): T {
        val operation = checkNotNull(active.value) { "Native callback has no active operation" }
        check(operation.generation == generation) { "Native callback belongs to a closed resource generation" }
        return withContext(operation.context + operation + NonCancellable) {
            check(active.value === operation) { "Native callback outlived its operation" }
            // Count the callback itself too, including nested authenticated storage work.
            access.withAccess { block() }
        }
    }

    fun transport(delegate: MLSTransporter, generation: Long): MLSTransporter = object : MLSTransporter {
        override suspend fun sendCommitBundle(commitBundle: CommitBundle) = callback(generation) {
            delegate.sendCommitBundle(commitBundle)
        }
    }

    fun pki(delegate: PkiEnvironmentHooks, generation: Long): PkiEnvironmentHooks = object : PkiEnvironmentHooks {
        override suspend fun httpRequest(
            method: PkiHttpMethod,
            url: String,
            headers: List<PkiHttpHeader>,
            body: ByteArray,
        ) = callback(generation) { delegate.httpRequest(method, url, headers, body) }

        override suspend fun authenticate(idp: String, keyAuth: String, acmeAud: String) = callback(generation) {
            delegate.authenticate(idp, keyAuth, acmeAud)
        }

        override suspend fun getBackendNonce() = callback(generation) { delegate.getBackendNonce() }
        override suspend fun fetchBackendAccessToken(dpop: String) = callback(generation) {
            delegate.fetchBackendAccessToken(dpop)
        }
    }

    private class Operation(
        val owner: CoreCryptoCallbackContext,
        val generation: Long,
        val context: CoroutineContext,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Operation>
    }
}
