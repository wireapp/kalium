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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal interface MLSClientProvider : CryptoBackupExporter {
    suspend fun isMLSClientInitialised(): Boolean

    suspend fun getMLSClient(clientId: ClientId? = null): Either<CoreFailure, MLSClient>

    suspend fun getCoreCrypto(clientId: ClientId? = null): Either<CoreFailure, CoreCryptoCentral>

    suspend fun clearLocalFiles()
    suspend fun getOrFetchMLSConfig(): Either<CoreFailure, SupportedCipherSuite>
}

@Suppress("LongParameterList")
internal class MLSClientProviderImpl(
    private val rootKeyStorePath: String,
    private val userId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val passphraseStorage: PassphraseStorage,
    private val userConfigRepository: UserConfigRepository,
    private val featureConfigRepository: FeatureConfigRepository,
    private val mlsTransportProvider: MLSTransportProvider,
    private val epochObserver: MLSEpochObserver,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val processingScope: CoroutineScope,
    private val coreCryptoCentralFactory: suspend (String, ByteArray) -> CoreCryptoCentral = { rootDir, passphrase ->
        coreCryptoCentral(rootDir = rootDir, passphrase = passphrase)
    },
) : MLSClientProvider, CryptoBackupExporter {

    private var mlsClient: MLSClient? = null
    private var coreCryptoCentral: CoreCryptoCentral? = null

    private val mlsClientMutex = Mutex()
    private val coreCryptoCentralMutex = Mutex()

    override suspend fun isMLSClientInitialised() = mlsClientMutex.withLock { mlsClient != null }

    override suspend fun getMLSClient(clientId: ClientId?): Either<CoreFailure, MLSClient> = mlsClientMutex.withLock {
        val currentClientId = clientId ?: currentClientIdProvider().fold({ return Either.Left(it) }, { it })
        val cryptoUserId = CryptoUserID(value = userId.value, domain = userId.domain)
        return mlsClient?.let {
            Either.Right(it)
        } ?: run {
            mlsClient(
                cryptoUserId,
                currentClientId,
                mlsTransportProvider,
                epochObserver,
            ).map {
                mlsClient = it
                return@run Either.Right(it)
            }
        }
    }

    override suspend fun getOrFetchMLSConfig(): Either<CoreFailure, SupportedCipherSuite> {
        if (!userConfigRepository.isMLSEnabled().getOrElse(true)) {
            kaliumLogger.w("$TAG: Cannot fetch MLS config, MLS is disabled.")
            return MLSFailure.Disabled.left()
        }
        return userConfigRepository.getSupportedCipherSuite().flatMapLeft {
            featureConfigRepository.getFeatureConfigs().map {
                it.mlsModel.supportedCipherSuite
            }.flatMap {
                it?.right() ?: CoreFailure.Unknown(Exception("No supported cipher suite found")).left()
            }
        }
    }

    override suspend fun clearLocalFiles() {
        mlsClientMutex.withLock {
            coreCryptoCentralMutex.withLock {
                val currentMLSClient = mlsClient
                val currentCoreCrypto = coreCryptoCentral
                try {
                    if (currentMLSClient != null) {
                        currentMLSClient.close()
                    } else {
                        currentCoreCrypto?.close()
                    }
                } finally {
                    mlsClient = null
                    coreCryptoCentral = null
                    FileUtil.deleteDirectory(rootKeyStorePath)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getCoreCrypto(clientId: ClientId?) = coreCryptoCentralMutex.withLock {
        withContext(dispatchers.io) {
            val currentClientId = clientId ?: currentClientIdProvider().fold({ return@withContext Either.Left(it) }, { it })

            val location = "$rootKeyStorePath/${currentClientId.value}".also {
                // TODO: migrate to okio solution once assert refactor is merged
                FileUtil.mkDirs(it)
            }
            val rootDir = "$location/$KEYSTORE_NAME"
            val dbSecret = SecurityHelperImpl(passphraseStorage).mlsDBSecret(userId, rootDir)
            return@withContext coreCryptoCentral?.let {
                Either.Right(it)
            } ?: run {
                val cc = try {
                    coreCryptoCentralFactory(rootDir, dbSecret.passphrase)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val logMap = mapOf(
                        "exception" to e,
                        "message" to e.message,
                        "stackTrace" to e.stackTraceToString()
                    )
                    kaliumLogger.logStructuredJson(KaliumLogLevel.ERROR, TAG, logMap)
                    return@run Either.Left(CoreFailure.Unknown(e))
                }
                coreCryptoCentral = cc
                Either.Right(cc)
            }
        }
    }

    private suspend fun mlsClient(
        userId: CryptoUserID,
        clientId: ClientId,
        mlsTransporter: MLSTransporter,
        epochObserver: MLSEpochObserver,
    ): Either<CoreFailure, MLSClient> {
        return getOrFetchMLSConfig().flatMap { (_, defaultCipherSuite) ->
            getCoreCrypto(clientId).map { cc ->
                cc.mlsClient(
                    clientId = CryptoQualifiedClientId(clientId.value, userId),
                    defaultCipherSuite = defaultCipherSuite.toCrypto(),
                    mlsTransporter = mlsTransporter,
                    epochObserver = epochObserver,
                    coroutineScope = processingScope
                )
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun exportCryptoDB(exportPath: String): Either<CoreFailure, CryptoBackupMetadata> =
        withContext(dispatchers.io) {
            currentClientIdProvider().fold(
                { return@withContext it.left() },
                { clientId ->
                    val location = "$rootKeyStorePath/${clientId.value}"
                    val rootDir = "$location/$KEYSTORE_NAME"
                    val cc = coreCryptoCentralMutex.withLock { coreCryptoCentral }
                        ?: return@withContext StorageFailure.DataNotFound.left()

                    try {
                        cc.exportDatabaseCopy(exportPath)
                    } catch (e: Exception) {
                        return@withContext CoreFailure.Unknown(e).left()
                    }

                    val dbSecret = SecurityHelperImpl(passphraseStorage)
                        .mlsDBSecret(userId, rootDir)

                    CryptoBackupMetadata(
                        dbPath = exportPath,
                        passphrase = dbSecret.passphrase,
                        clientId = clientId
                    ).right()
                }
            )
        }

    private companion object {
        const val KEYSTORE_NAME = "keystore"
        const val TAG = "MLSClientProvider"
    }
}
