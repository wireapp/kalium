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
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.cryptography.cryptoboxProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusStorageMigrationException
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.logStructuredJson
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.logic.wrapProteusRequest
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val proteusMigrationRecoveryHandler: ProteusMigrationRecoveryHandler
) : ProteusClientProvider {

    private var _proteusClient: ProteusClient? = null
    private val mutex = Mutex()

    override suspend fun clearLocalFiles() {
        mutex.withLock {
            withContext(dispatcher.io) {
                _proteusClient?.close()
                _proteusClient = null
                FileUtil.deleteDirectory(rootProteusPath)
            }
        }
    }

    override suspend fun getOrCreate(): ProteusClient {
        mutex.withLock {
            return _proteusClient ?: createProteusClient().also {
                _proteusClient = it
            }
        }
    }

    override suspend fun getOrError(): Either<CoreFailure, ProteusClient> {
        return mutex.withLock {
            withContext(dispatcher.io) {
                _proteusClient?.let { Either.Right(it) } ?: run {
                    if (FileUtil.isDirectoryNonEmpty(rootProteusPath)) {
                        wrapProteusRequest {
                            createProteusClient().also {
                                _proteusClient = it
                            }
                        }
                    } else {
                        Either.Left(CoreFailure.MissingClientRegistration)
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createProteusClient(): ProteusClient {
        return if (kaliumConfigs.encryptProteusStorage) {
            val central = try {
                coreCryptoCentral(
                    rootDir = rootProteusPath,
                    databaseKey = SecurityHelperImpl(passphraseStorage).proteusDBSecret(userId).value
                )
            } catch (e: Exception) {
                val logMap = mapOf(
                    "userId" to userId.value.obfuscateId(),
                    "exception" to e,
                    "message" to e.message,
                    "stackTrace" to e.stackTraceToString()
                )
                kaliumLogger.logStructuredJson(KaliumLogLevel.ERROR, TAG, logMap)
                throw e
            }
            getCentralProteusClientOrError(central)
        } else {
            cryptoboxProteusClient(
                rootDir = rootProteusPath,
                defaultContext = dispatcher.default,
                ioContext = dispatcher.io
            )
        }
    }

    private suspend fun getCentralProteusClientOrError(central: CoreCryptoCentral): ProteusClient {
        return try {
            central.proteusClient()
        } catch (exception: ProteusStorageMigrationException) {
            proteusMigrationRecoveryHandler.clearClientData()
            val logMap = mapOf(
                "userId" to userId.value.obfuscateId(),
                "exception" to exception,
                "message" to exception.message,
                "stackTrace" to exception.stackTraceToString()
            )
            kaliumLogger.withTextTag(TAG).logStructuredJson(KaliumLogLevel.ERROR, "Proteus storage migration failed", logMap)
            throw exception
        }
    }

    private companion object {
        const val TAG = "ProteusClientProvider"
    }
}
