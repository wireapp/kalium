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
package com.wire.kalium.logic.util.arrangement.provider

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import io.mockative.any
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock

internal interface CryptoTransactionProviderArrangement {
    val cryptoTransactionProvider: CryptoTransactionProvider
    val proteusContext: ProteusCoreCryptoContext
    val mlsContext: MlsCoreCryptoContext
    val transactionContext: CryptoTransactionContext

    suspend fun <R> withTransactionReturning(result: Either<CoreFailure, R>): CryptoTransactionProviderArrangement
    suspend fun <R> withProteusTransactionReturning(result: Either<CoreFailure, R>): CryptoTransactionProviderArrangement
    suspend fun <R> withProteusTransactionResultOnly(result: Either<CoreFailure, R>): CryptoTransactionProviderArrangement
    suspend fun <R> withMLSTransactionReturning(result: Either<CoreFailure, R>): CryptoTransactionProviderArrangement

}

internal class CryptoTransactionProviderArrangementImpl : CryptoTransactionProviderArrangement {

    override val proteusContext: ProteusCoreCryptoContext = mock(ProteusCoreCryptoContext::class)
    override val mlsContext: MlsCoreCryptoContext = mock(MlsCoreCryptoContext::class)
    override val transactionContext = mock(CryptoTransactionContext::class)

    override val cryptoTransactionProvider: CryptoTransactionProvider = mock(CryptoTransactionProvider::class)

    init {
        every { transactionContext.proteus } returns proteusContext
        every { transactionContext.mls } returns mlsContext
    }

    override suspend fun <R> withTransactionReturning(result: Either<CoreFailure, R>): CryptoTransactionProviderArrangement = apply {
        coEvery {
            cryptoTransactionProvider.transaction<R>(any(), any())
        }.invokes { args ->
            @Suppress("UNCHECKED_CAST")
            val block = args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, R>
            block(transactionContext)
        }
    }

    override suspend fun <R> withProteusTransactionReturning(
        result: Either<CoreFailure, R>
    ): CryptoTransactionProviderArrangement = apply {
        coEvery {
            cryptoTransactionProvider.proteusTransaction<R>(any(), any())
        }.invokes { args ->
            @Suppress("UNCHECKED_CAST")
            val block = args[1] as suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, R>
            block(proteusContext)
        }
    }

    override suspend fun <R> withProteusTransactionResultOnly(
        result: Either<CoreFailure, R>
    ): CryptoTransactionProviderArrangement = apply {
        coEvery {
            cryptoTransactionProvider.proteusTransaction<R>(any(), any())
        }.returns(result)
    }

    override suspend fun <R> withMLSTransactionReturning(result: Either<CoreFailure, R>): CryptoTransactionProviderArrangement = apply {
        coEvery {
            cryptoTransactionProvider.mlsTransaction<R>(any(), any())
        }.invokes { args ->
            @Suppress("UNCHECKED_CAST")
            val block = args[1] as suspend (MlsCoreCryptoContext) -> Either<CoreFailure, R>
            block(mlsContext)
        }
    }
}
