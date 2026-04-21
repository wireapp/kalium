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
@file:OptIn(InternalCryptoAccess::class)

package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.util.InternalCryptoAccess
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoTransactionProviderCrashTest {

    @Test
    fun givenProteusTransactionThrowsIllegalStateException_thenReturnsMissingClientFailure() = runTest {
        val (_, provider) = Arrangement()
            .withProteusTransactionThrowing(IllegalStateException("CoreCrypto object has already been destroyed"))
            .arrange()

        val result = provider.proteusTransaction<Unit>("test") {
            error("should not be called")
        }

        result.shouldFail {
            assertEquals(CoreFailure.MissingClientRegistration, it)
        }
    }

    @Test
    fun givenMLSTransactionThrowsIllegalStateException_thenReturnsMLSFailureDisabled() = runTest {
        val (_, provider) = Arrangement()
            .withMlsTransactionThrowing(IllegalStateException("CoreCrypto object has already been destroyed"))
            .arrange()

        val result = provider.mlsTransaction<Unit>("test") {
            error("should not be called")
        }

        result.shouldFail {
            assertEquals(MLSFailure.Disabled, it)
        }
    }

    @Test
    fun givenMainTransactionWhenProteusClientThrows_thenReturnsMissingClientFailure() = runTest {
        val (_, provider) = Arrangement()
            .withProteusTransactionThrowing(IllegalStateException("CoreCrypto object has already been destroyed"))
            .withMlsTransactionSucceeds()
            .arrange()

        val result = provider.transaction<Unit>("test") {
            error("should not be called")
        }

        result.shouldFail {
            assertEquals(CoreFailure.MissingClientRegistration, it)
        }
    }

    @Test
    fun givenMainTransactionWhenMLSClientThrows_thenReturnsMissingClientFailure() = runTest {
        val (_, provider) = Arrangement()
            .withProteusTransactionSucceeds()
            .withMlsTransactionThrowing(IllegalStateException("CoreCrypto object has already been destroyed"))
            .arrange()

        val result = provider.transaction<Unit>("test") {
            error("should not be called")
        }

        result.shouldFail {
            assertEquals(CoreFailure.MissingClientRegistration, it)
        }
    }

    private class Arrangement {
        val proteusClient: ProteusClient = mock(mode = MockMode.autoUnit)
        val mlsClient: MLSClient = mock(mode = MockMode.autoUnit)

        val proteusContext: ProteusCoreCryptoContext = mock(mode = MockMode.autoUnit)
        val mlsContext: MlsCoreCryptoContext = mock(mode = MockMode.autoUnit)

        val proteusProvider: ProteusClientProvider = mock(mode = MockMode.autoUnit)
        val mlsProvider: MLSClientProvider = mock(mode = MockMode.autoUnit)

        fun withProteusTransactionSucceeds() = apply {
            everySuspend {
                proteusClient.transaction(any(), any<suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, Unit>>())
            } calls { invocation ->
                val block = invocation.args[1] as suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, Unit>
                block(proteusContext)
            }
        }

        fun withProteusTransactionThrowing(e: Throwable) = apply {
            everySuspend {
                proteusClient.transaction<String>(any(), any())
            } throws e
        }

        fun withMlsTransactionSucceeds() = apply {
            everySuspend {
                mlsClient.transaction(any(), any<suspend (MlsCoreCryptoContext) -> Either<CoreFailure, String>>())
            } calls { invocation ->
                val block = invocation.args[1] as suspend (MlsCoreCryptoContext) -> Either<CoreFailure, String>
                block(mlsContext)
            }
        }

        fun withMlsTransactionThrowing(e: Throwable) = apply {
            everySuspend {
                mlsClient.transaction<String>(any(), any())
            } throws e
        }

        fun arrange(): Pair<Arrangement, CryptoTransactionProvider> {
            everySuspend { proteusProvider.getOrError() } returns proteusClient.right()
            everySuspend { mlsProvider.getMLSClient() } returns mlsClient.right()
            return this to CryptoTransactionProviderImpl(mlsProvider, proteusProvider)
        }
    }
}
