/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.util.InternalKaliumApi

/** Proteus and, when registered, MLS transactions over an owned account's stores, for sending. */
@InternalKaliumApi
public class AppleCryptoTransactions internal constructor(
    private val proteus: ProteusClient,
    private val mls: MLSClient?,
) {
    /** Runs [block] in a Proteus transaction with a nested MLS transaction, as the app does. */
    public suspend fun <R> transaction(name: String, block: suspend (CryptoTransactionContext) -> R): R =
        proteus.transaction("proteus_$name") { proteusContext ->
            if (mls == null) block(context(proteusContext, null))
            else mls.transaction("mls_$name") { mlsContext -> block(context(proteusContext, mlsContext)) }
        }

    private fun context(proteus: ProteusCoreCryptoContext, mls: MlsCoreCryptoContext?) = object : CryptoTransactionContext {
        override val mls: MlsCoreCryptoContext? = mls
        override val proteus: ProteusCoreCryptoContext = proteus
    }
}

/** Crypto transactions for sending from this owned account; see [AppleCryptoTransactions]. */
@InternalKaliumApi
public suspend fun AppleAccountRuntime.cryptoTransactions(): AppleCryptoTransactions = AppleCryptoTransactions(
    proteusCoreCrypto.proteusClient(),
    mlsClient(ReceivedMLSRejoin(network.mlsMessageApi, database.metadataDAO)),
)

internal suspend fun AppleAccountRuntime.mlsClient(rejoin: ReceivedMLSRejoin): MLSClient? = mlsCoreCrypto?.mlsClient(
    CryptoQualifiedClientId(prepared.clientId, prepared.userId.toCrypto()),
    checkNotNull(mlsCipherSuite).toCrypto(),
    rejoin,
    object : MLSEpochObserver {
        override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) {
            // Required epoch work is checked synchronously in the receiving transaction.
            // Native observer callbacks arrive asynchronously, including after rollback.
        }
    },
    processingScope,
)
