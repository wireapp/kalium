/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cryptography

import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoCommand
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.CoreCryptoFfiInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.seconds

/**
 * Performs a work using a CC Transaction, returning its result, exactly like the regular withLock function.
 * However, this will monitor the work and log warnings using the provided [workIdentifier]
 * every 10 seconds, while the work isn't completed.
 */
@Suppress("MagicNumber")
internal suspend fun <T> CoreCrypto.transaction(
    workIdentifier: String,
    block: suspend (context: CoreCryptoContext) -> T
): T = coroutineScope {
    withContext(Dispatchers.Default) {
        val workUniqueId = "$workIdentifier;${Random.nextUInt()}"
        val asyncLockWork = async {
            val result = transactionWithRollbackPolicy {
                kaliumLogger.d("CC Transaction '$workUniqueId' started.")
                try {
                    block(it)
                } finally {
                    // The callback owns a UniFFI context handle that otherwise waits for GC.
                    it.close()
                }
            }
            kaliumLogger.d("CC Transaction '$workUniqueId' completed.")
            result
        }
        val startInstant = Clock.System.now()
        val waitJob = launch {
            while (asyncLockWork.isActive) {
                delay(10.seconds)
                if (asyncLockWork.isActive) {
                    val currentInstant = Clock.System.now()
                    val elapsedTime = currentInstant.minus(startInstant)
                    kaliumLogger.w(
                        "Waiting for CC Transaction '$workUniqueId' to complete for a long time! Elapsed time: $elapsedTime."
                    )
                }
            }
        }
        asyncLockWork.invokeOnCompletion {
            waitJob.cancel()
        }
        asyncLockWork.await()
    }
}

private suspend fun <T> CoreCrypto.transactionWithRollbackPolicy(block: suspend (CoreCryptoContext) -> T): T =
    if (currentCoroutineContext()[RequireConfirmedCryptoRollback.Key] != null) {
        transactionWithConfirmedRollback(block)
    } else {
        transaction(block)
    }

@Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")
private suspend fun <T> CoreCrypto.transactionWithConfirmedRollback(block: suspend (CoreCryptoContext) -> T): T =
    withContext(NonCancellable) {
        var result: T? = null
        var callbackCompleted = false
        var callbackFailure: Throwable? = null
        val nativeClient: CoreCryptoFfiInterface = this@transactionWithConfirmedRollback
        try {
            nativeClient.transactionFfi(object : CoreCryptoCommand {
                override suspend fun execute(context: CoreCryptoContext) {
                    try {
                        result = block(context)
                        callbackCompleted = true
                    } catch (failure: Throwable) {
                        callbackFailure = failure
                        // Typed CoreCrypto exceptions cross UniFFI unchanged. A nonnative sentinel
                        // makes successful abort return TransactionFailed for every callback error.
                        throw CryptoRollbackRequested()
                    }
                }
            })
        } catch (failure: CoreCryptoException.TransactionFailed) {
            // CoreCrypto 10.5.1 returns the callback error only after abort succeeds.
            // Other native errors (including abort and commit failures) must win.
            throw callbackFailure ?: failure
        }
        check(callbackFailure == null && callbackCompleted) {
            "CoreCrypto returned without confirming the transaction outcome"
        }
        result as T
    }

private class CryptoRollbackRequested : Exception("kalium-confirm-crypto-rollback")
