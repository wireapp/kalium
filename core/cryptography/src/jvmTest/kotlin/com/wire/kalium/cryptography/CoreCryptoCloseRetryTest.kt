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

import com.wire.crypto.CipherSuite
import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoCommand
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.CoreCryptoFfi
import com.wire.crypto.Database
import com.wire.crypto.NoPointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class CoreCryptoCloseRetryTest {

    @Test
    fun rollbackFailureKeepsNativeResourcesOwnedAndCannotBecomeSuccessOnRetry() = runTest {
        val coreCrypto = RecordingCoreCryptoFfi(rollbackFailure = true)
        val database = RecordingDatabase()
        val central = central(coreCrypto, database)

        val failure = assertFailsWith<CoreCryptoException.Other> { central.close() }
        assertEquals(failure, assertFailsWith<CoreCryptoException.Other> { central.close() })
        assertEquals(0, coreCrypto.closeCount)
        assertEquals(0, database.closeCount)
    }

    @Test
    fun databaseCloseFailureNeverBecomesASuccessfulClose() = runTest {
        val coreCrypto = RecordingCoreCryptoFfi()
        val database = RecordingDatabase(failures = 1)
        val central = central(coreCrypto, database)

        val failure = assertFailsWith<IllegalStateException> { central.close() }
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { central.close() }.message)
        assertEquals(1, coreCrypto.closeCount)
        assertEquals(1, database.closeCount)
    }

    @Test
    fun coreCryptoCloseFailureKeepsDatabaseAliveAndNeverBecomesASuccess() = runTest {
        val coreCrypto = RecordingCoreCryptoFfi(failures = 1)
        val database = RecordingDatabase()
        val central = central(coreCrypto, database)

        val failure = assertFailsWith<IllegalStateException> { central.close() }
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { central.close() }.message)
        assertEquals(1, coreCrypto.closeCount)
        assertEquals(0, database.closeCount)
    }

    @Test
    fun successfulCentralCloseIsIdempotent() = runTest {
        val coreCrypto = RecordingCoreCryptoFfi()
        val database = RecordingDatabase()
        val central = central(coreCrypto, database)

        central.close()
        central.close()

        assertEquals(1, coreCrypto.closeCount)
        assertEquals(1, database.closeCount)
    }

    @Test
    fun mlsClientCannotHideNativeCloseFailureOnRetry() = runTest {
        val native = RecordingCoreCryptoFfi(failures = 1)
        val central = central(native, RecordingDatabase())
        val client = client { central.close() }

        val failure = assertFailsWith<IllegalStateException> { client.close() }
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { client.close() }.message)
        assertEquals(1, native.closeCount)
    }

    @Test
    fun mlsCloseFailureIsRetriedAndSuccessfulCloseIsIdempotent() = runTest {
        var closeCount = 0
        val client = client {
            closeCount++
            if (closeCount == 1) error("central close failed")
        }

        assertFailsWith<IllegalStateException> { client.close() }
        client.close()
        client.close()

        assertEquals(2, closeCount)
    }

    @Test
    fun callerCancellationDoesNotInterruptMlsClose() = runTest {
        val closeStarted = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        var closeCount = 0
        val client = client {
            closeCount++
            closeStarted.complete(Unit)
            allowClose.await()
        }
        val attempt = async(start = CoroutineStart.UNDISPATCHED) { client.close() }

        closeStarted.await()
        attempt.cancel()
        allowClose.complete(Unit)
        attempt.join()

        assertTrue(attempt.isCancelled)
        client.close()
        assertEquals(1, closeCount)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun cancelledConcurrentCloseWaitsForTheSameCleanup() = runTest {
        val closeStarted = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        var closeCount = 0
        val client = client {
            closeCount++
            closeStarted.complete(Unit)
            allowClose.await()
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { client.close() }
        closeStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { client.close() }
        second.cancel()
        runCurrent()
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)

        allowClose.complete(Unit)
        first.await()
        second.join()
        assertTrue(second.isCancelled)
        assertEquals(1, closeCount)
    }

    private fun central(
        coreCrypto: RecordingCoreCryptoFfi,
        database: RecordingDatabase,
    ): CoreCryptoCentralImpl = CoreCryptoCentralImpl(
        cc = CoreCrypto(coreCrypto),
        rootDir = "/unused",
        database = database,
    )

    private suspend fun client(onClose: suspend () -> Unit): MLSClientImpl = MLSClientImpl.create(
        coreCrypto = CoreCrypto(RecordingCoreCryptoFfi()),
        clientId = ClientId(NoPointer),
        defaultCipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519,
        onClose = onClose,
    )

    private class RecordingCoreCryptoFfi(failures: Int = 0, private val rollbackFailure: Boolean = false) : CoreCryptoFfi(NoPointer) {
        private var remainingFailures = failures
        var closeCount = 0
            private set

        override suspend fun transactionFfi(command: CoreCryptoCommand) {
            val context = object : CoreCryptoContext(NoPointer) {
                override fun close() = Unit
            }
            try {
                command.execute(context)
            } catch (failure: IllegalStateException) {
                if (rollbackFailure) throw CoreCryptoException.Other("Native rollback failed")
                throw CoreCryptoException.TransactionFailed(failure.toString())
            }
        }

        override fun close() {
            closeCount++
            if (remainingFailures > 0) {
                remainingFailures--
                error("CoreCrypto close failed")
            }
        }
    }

    private class RecordingDatabase(failures: Int = 0) : Database(NoPointer) {
        private var remainingFailures = failures
        var closeCount = 0
            private set

        override fun close() {
            closeCount++
            if (remainingFailures > 0) {
                remainingFailures--
                error("database close failed")
            }
        }
    }
}
