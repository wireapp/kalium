/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.cryptography

import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoCommand
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.CoreCryptoFfi
import com.wire.crypto.NoPointer
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class CoreCryptoConfirmedRollbackTest {

    @Test
    fun confirmedRollbackRestoresOriginalCallbackFailure() = runTest {
        val callbackFailure = DiscardSpeculativeWork()
        val native = RecordingCoreCryptoFfi()

        val actual = assertFailsWith<DiscardSpeculativeWork> {
            withContext(RequireConfirmedCryptoRollback) {
                CoreCrypto(native).transaction("confirmed-rollback") { throw callbackFailure }
            }
        }

        assertSame(callbackFailure, actual)
        assertNotSame(callbackFailure, native.callbackFailure)
        assertEquals(1, native.contextCloseCount)
    }

    @Test
    fun typedCallbackFailureAlsoRequiresConfirmedRollback() = runTest {
        val callbackFailure = CoreCryptoException.Other("callback operation failed")
        val native = RecordingCoreCryptoFfi()

        val actual = assertFailsWith<CoreCryptoException.Other> {
            withContext(RequireConfirmedCryptoRollback) {
                CoreCrypto(native).transaction("typed-callback") { throw callbackFailure }
            }
        }

        assertSame(callbackFailure, actual)
        assertNotSame(callbackFailure, native.callbackFailure)
        assertEquals(1, native.contextCloseCount)
    }

    @Test
    fun nativeRollbackFailureOverridesCallbackFailure() = runTest {
        val callbackFailure = DiscardSpeculativeWork()
        val rollbackFailure = CoreCryptoException.Other("native abort failed")
        val native = RecordingCoreCryptoFfi(rollbackFailure = rollbackFailure)

        val actual = assertFailsWith<CoreCryptoException.Other> {
            withContext(RequireConfirmedCryptoRollback) {
                CoreCrypto(native).transaction("failed-rollback") { throw callbackFailure }
            }
        }

        assertSame(rollbackFailure, actual)
        assertNotSame<Throwable>(callbackFailure, actual)
        assertEquals(1, native.contextCloseCount)
    }

    @Test
    fun nativeCommitFailureIsPropagated() = runTest {
        val commitFailure = CoreCryptoException.Other("native commit failed")
        val native = RecordingCoreCryptoFfi(commitFailure = commitFailure)

        val actual = assertFailsWith<CoreCryptoException.Other> {
            withContext(RequireConfirmedCryptoRollback) {
                CoreCrypto(native).transaction("failed-commit") { "result" }
            }
        }

        assertSame(commitFailure, actual)
        assertEquals(1, native.contextCloseCount)
    }

    @Test
    fun transactionFailedWithoutCallbackFailureIsPropagated() = runTest {
        val commitFailure = CoreCryptoException.TransactionFailed("unexpected native transaction failure")
        val native = RecordingCoreCryptoFfi(commitFailure = commitFailure)

        val actual = assertFailsWith<CoreCryptoException.TransactionFailed> {
            withContext(RequireConfirmedCryptoRollback) {
                CoreCrypto(native).transaction("native-transaction-failure") { "result" }
            }
        }

        assertSame(commitFailure, actual)
    }

    @Test
    fun successfulTransactionPreservesResultAndClosesContext() = runTest {
        val expected = Any()
        val native = RecordingCoreCryptoFfi()

        val actual = withContext(RequireConfirmedCryptoRollback) {
            CoreCrypto(native).transaction("successful-transaction") { expected }
        }

        assertSame(expected, actual)
        assertEquals(1, native.contextCloseCount)
    }

    @Test
    fun successfulNullableTransactionPreservesNull() = runTest {
        val native = RecordingCoreCryptoFfi()

        val actual = withContext(RequireConfirmedCryptoRollback) {
            CoreCrypto(native).transaction<String?>("nullable-transaction") { null }
        }

        assertNull(actual)
        assertEquals(1, native.contextCloseCount)
    }

    @Test
    fun normalNativeReturnAfterCallbackFailureDoesNotRestoreDiscardSignal() = runTest {
        val callbackFailure = DiscardSpeculativeWork()
        val native = RecordingCoreCryptoFfi(ignoreCallbackFailure = true)

        val actual = assertFailsWith<IllegalStateException> {
            withContext(RequireConfirmedCryptoRollback) {
                CoreCrypto(native).transaction("unconfirmed-rollback") { throw callbackFailure }
            }
        }

        assertNotSame<Throwable>(callbackFailure, actual)
        // Coroutine stacktrace recovery may add copies of the boundary error as causes.
        assertFalse(generateSequence<Throwable>(actual) { it.cause }.any { it is DiscardSpeculativeWork })
        assertEquals(1, native.contextCloseCount)
    }

    @Test
    fun defaultTransactionRetainsUpstreamCallbackErrorPrecedence() = runTest {
        val callbackFailure = DiscardSpeculativeWork()
        val native = RecordingCoreCryptoFfi(rollbackFailure = CoreCryptoException.Other("native abort failed"))

        val actual = assertFailsWith<DiscardSpeculativeWork> {
            CoreCrypto(native).transaction("default-transaction") { throw callbackFailure }
        }

        assertSame(callbackFailure, actual)
        assertSame(callbackFailure, native.callbackFailure)
        assertEquals(1, native.contextCloseCount)
    }

    // Extra state prevents coroutine stacktrace recovery from reflectively copying this control signal.
    private class DiscardSpeculativeWork(val transactionIdentity: Any = Any()) : Exception("discard speculative work")

    private class RecordingCoreCryptoFfi(
        private val rollbackFailure: CoreCryptoException? = null,
        private val commitFailure: CoreCryptoException? = null,
        private val ignoreCallbackFailure: Boolean = false,
    ) : CoreCryptoFfi(NoPointer) {
        var contextCloseCount = 0
            private set
        var callbackFailure: Throwable? = null
            private set

        @Suppress("TooGenericExceptionCaught")
        override suspend fun transactionFfi(command: CoreCryptoCommand) {
            val context = object : CoreCryptoContext(NoPointer) {
                override fun close() {
                    contextCloseCount++
                }
            }
            try {
                command.execute(context)
            } catch (failure: Throwable) {
                callbackFailure = failure
                if (ignoreCallbackFailure) return
                rollbackFailure?.let { throw it }
                // Model UniFFI: typed errors survive; unexpected callback errors become TransactionFailed.
                throw failure as? CoreCryptoException ?: CoreCryptoException.TransactionFailed(failure.toString())
            }
            commitFailure?.let { throw it }
        }
    }
}
