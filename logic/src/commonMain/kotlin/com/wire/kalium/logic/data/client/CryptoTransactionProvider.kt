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
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.util.InternalCryptoAccess
import io.mockative.Mockable

/**
 * Provides transactional access to cryptographic operations using either
 * Proteus or a combined context (Proteus + MLS).
 *
 * This interface is designed to encapsulate the logic of obtaining and executing
 * transactional operations on cryptographic clients in a safe, composable way.
 *
 * The typical usage pattern is to call one of the `transaction(...)` methods with
 * a suspending lambda that operates on the appropriate crypto context. The method ensures
 * the client is resolved, a transaction is started, and the provided block is executed
 * within that context.
 *
 * Example:
 * ```
 * cryptoTransactionProvider.proteusTransaction { proteus ->
 *     proteus.encrypt(message, sessionId)
 * }
 * ```
 */
@Mockable
interface CryptoTransactionProvider {
    val mlsClientProvider: MLSClientProvider
    val proteusClientProvider: ProteusClientProvider
    suspend fun <R> proteusTransaction(
        name: String? = null,
        block: suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R>

    suspend fun <R> mlsTransaction(
        name: String? = null,
        block: suspend (MlsCoreCryptoContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R>

    suspend fun <R> transaction(
        name: String? = null,
        block: suspend (transactionContext: CryptoTransactionContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R>
}

class CryptoTransactionProviderImpl(
    override val mlsClientProvider: MLSClientProvider,
    override val proteusClientProvider: ProteusClientProvider
) : CryptoTransactionProvider {

    @OptIn(InternalCryptoAccess::class)
    override suspend fun <R> proteusTransaction(
        name: String?,
        block: suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R> {
        return proteusClientProvider.getOrError()
            .flatMap { proteus ->
                try {
                    proteus.transaction(name.toTransactionName("proteus")) { proteusContext ->
                        block(proteusContext)
                    }
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("already been destroyed") == true) {
                        kaliumLogger.w("Tried to use destroyed ProteusClient in transaction '$name'", e)
                        CoreFailure.MissingClientRegistration.left()
                    } else {
                        throw e
                    }
                }
            }
    }

    override suspend fun <R> mlsTransaction(
        name: String?,
        block: suspend (MlsCoreCryptoContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R> {
        return mlsClientProvider.getMLSClient()
            .flatMap { mlsClient ->
                try {
                    mlsClient.transaction(name.toTransactionName("mls")) { mlsContext ->
                        block(mlsContext)
                    }
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("already been destroyed") == true) {
                        kaliumLogger.w("Tried to use destroyed MLSClient in transaction '$name'", e)
                        MLSFailure.Disabled.left()
                    } else {
                        throw e
                    }
                }
            }
    }

    @OptIn(InternalCryptoAccess::class)
    override suspend fun <R> transaction(
        name: String?,
        block: suspend (CryptoTransactionContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R> {
        val proteusClient = proteusClientProvider.getOrError().getOrNull()
            ?: return CoreFailure.MissingClientRegistration.left()
        val mlsClient = mlsClientProvider.getMLSClient().getOrNull()

        return try {
            proteusClient.transaction(name.toTransactionName("proteus")) { proteusCtx ->
                mlsClient?.transaction(name.toTransactionName("mls")) { mlsCtx ->
                    block(withCryptoContext(proteusCtx, mlsCtx))
                } ?: block(withCryptoContext(proteusCtx, null))
            }
        } catch (e: IllegalStateException) {
            if (e.message?.contains("already been destroyed") == true) {
                kaliumLogger.w("Tried to use destroyed crypto client in combined transaction '$name'", e)
                CoreFailure.MissingClientRegistration.left()
            } else {
                throw e
            }
        }
    }

    private fun withCryptoContext(proteus: ProteusCoreCryptoContext, mls: MlsCoreCryptoContext?) = object : CryptoTransactionContext {
        override val mls: MlsCoreCryptoContext? = mls
        override val proteus: ProteusCoreCryptoContext = proteus
    }

    private fun String?.toTransactionName(protocol: String): String {
        return this?.let { "${protocol}_$it" } ?: protocol
    }
}
