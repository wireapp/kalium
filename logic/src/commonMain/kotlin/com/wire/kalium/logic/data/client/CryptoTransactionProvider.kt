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

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusCoreCryptoContext

interface CryptoTransactionProvider {
    val mlsClientProvider: MLSClientProvider
    val proteusClientProvider: ProteusClientProvider

    suspend fun <R> transaction(
        name: String? = null,
        block: suspend (CryptoTransactionContext) -> R
    ): R
}

class CryptoTransactionProviderImpl(
    override val mlsClientProvider: MLSClientProvider,
    override val proteusClientProvider: ProteusClientProvider
) : CryptoTransactionProvider {

    override suspend fun <R> transaction(
        name: String?,
        block: suspend (CryptoTransactionContext) -> R
    ): R {
        val mlsClient = mlsClientProvider.getMLSClient().getOrNull()
        val proteusClient = proteusClientProvider.getOrError().getOrNull()

        if (mlsClient == null && proteusClient == null) {
            error("At least one of MLS or Proteus client must be available")
        }

        return if (mlsClient != null) {
            mlsClient.transaction(name ?: "crypto") { mlsCtx ->
                if (proteusClient != null) {
                    proteusClient.transaction(name ?: "crypto") { proteusCtx ->
                        block(object : CryptoTransactionContext {
                            override val mls: MlsCoreCryptoContext = mlsCtx
                            override val proteus: ProteusCoreCryptoContext = proteusCtx
                        })
                    }
                } else {
                    block(object : CryptoTransactionContext {
                        override val mls: MlsCoreCryptoContext = mlsCtx
                        override val proteus: ProteusCoreCryptoContext? = null
                    })
                }
            }
        } else {
            // Only Proteus available
            proteusClient!!.transaction(name ?: "crypto") { proteusCtx ->
                block(object : CryptoTransactionContext {
                    override val mls: MlsCoreCryptoContext? = null
                    override val proteus: ProteusCoreCryptoContext = proteusCtx
                })
            }
        }
    }

}

