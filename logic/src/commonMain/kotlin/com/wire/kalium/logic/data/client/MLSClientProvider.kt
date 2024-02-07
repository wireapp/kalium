/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface MLSClientProvider {
    suspend fun getMLSClient(clientId: ClientId? = null): Either<CoreFailure, MLSClient>

    suspend fun getCoreCrypto(clientId: ClientId? = null): Either<CoreFailure, CoreCryptoCentral>

    suspend fun clearLocalFiles()
}

class MLSClientProviderImpl(
    private val rootKeyStorePath: String,
    private val userId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val passphraseStorage: PassphraseStorage,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : MLSClientProvider {

    private var mlsClient: MLSClient? = null
    private var coreCryptoCentral: CoreCryptoCentral? = null

    override suspend fun getMLSClient(clientId: ClientId?): Either<CoreFailure, MLSClient> = withContext(dispatchers.io) {
        val currentClientId = clientId ?: currentClientIdProvider().fold({ return@withContext Either.Left(it) }, { it })
        val cryptoUserId = CryptoUserID(value = userId.value, domain = userId.domain)
        return@withContext mlsClient?.let {
            Either.Right(it)
        } ?: run {
            mlsClient(
                cryptoUserId,
                currentClientId
            ).map {
                mlsClient = it
                return@run Either.Right(it)
            }
        }
    }

    override suspend fun clearLocalFiles() {
        mlsClient?.close()
        mlsClient = null
        FileUtil.deleteDirectory(rootKeyStorePath)
    }

    override suspend fun getCoreCrypto(clientId: ClientId?) = withContext(dispatchers.io) {
        val currentClientId = clientId ?: currentClientIdProvider().fold({ return@withContext Either.Left(it) }, { it })

        val location = "$rootKeyStorePath/${currentClientId.value}".also {
            // TODO: migrate to okio solution once assert refactor is merged
            FileUtil.mkDirs(it)
        }
        val passphrase = SecurityHelperImpl(passphraseStorage).mlsDBSecret(userId).value
        return@withContext coreCryptoCentral?.let {
            Either.Right(it)
        } ?: run {
            val cc = coreCryptoCentral(
                rootDir = "$location/$KEYSTORE_NAME",
                databaseKey = passphrase
            )
            coreCryptoCentral = cc
            Either.Right(cc)
        }
    }

    private suspend fun mlsClient(userId: CryptoUserID, clientId: ClientId): Either<CoreFailure, MLSClient> {
        return getCoreCrypto(clientId).map {
            it.mlsClient(CryptoQualifiedClientId(clientId.value, userId))
        }
    }

    private companion object {
        const val KEYSTORE_NAME = "keystore"
    }
}
