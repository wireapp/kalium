/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

interface ProteusClientProvider {
    suspend fun clearLocalFiles()

    /**
     * Returns the ProteusClient or creates new one if doesn't exists.
     */
    suspend fun getOrCreate(): ProteusClient

    /**
     * Returns the ProteusClient, retrieves it from local files or returns a failure if local files doesn't exist.
     */
    suspend fun getOrError(): Either<CoreFailure, ProteusClient>
}

class ProteusClientProviderImpl(
    private val rootProteusPath: String,
    private val userId: UserId,
    private val passphraseStorage: PassphraseStorage,
    private val kaliumConfigs: KaliumConfigs,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ProteusClientProvider {

    private var _proteusClient: ProteusClient? = null
    private val mutex = Mutex()

    @Throws(ProteusException::class)
    override suspend fun clearLocalFiles() {
        mutex.withLock {
            withContext(dispatcher.io) {
                _proteusClient?.clearLocalFiles()
                _proteusClient = null
            }
        }
    }

    @Throws(ProteusException::class, CancellationException::class)
    override suspend fun getOrCreate(): ProteusClient {
        mutex.withLock {
            return _proteusClient ?: createProteusClient().also {
                it.openOrCreate()
                _proteusClient = it
            }
        }
    }

    override suspend fun getOrError(): Either<CoreFailure, ProteusClient> {
        return mutex.withLock {
            _proteusClient?.let { Either.Right(it) } ?: run {
                wrapCryptoRequest {
                    createProteusClient().also {
                        it.openOrError()
                        _proteusClient = it
                    }
                }.mapLeft {
                    if (it.proteusException.code == ProteusException.Code.LOCAL_FILES_NOT_FOUND) CoreFailure.MissingClientRegistration
                    else it
                }
            }
        }
    }

    private fun createProteusClient(): ProteusClient {
        return if (kaliumConfigs.encryptProteusStorage) {
            ProteusClientImpl(
                rootProteusPath,
                SecurityHelperImpl(passphraseStorage).proteusDBSecret(userId),
                defaultContext = dispatcher.default,
                ioContext = dispatcher.io
            )
        } else {
            ProteusClientImpl(
                rootProteusPath, null,
                defaultContext = dispatcher.default,
                ioContext = dispatcher.io
            )
        }
    }
}
