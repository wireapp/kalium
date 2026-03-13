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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException

/**
 * Applies crypto state (MLS and Proteus keystores) from extracted backup files.
 * This replaces the existing keystores with the ones from the backup and updates
 * the database passphrases in storage.
 */
internal interface ApplyCryptoStateUseCase {
    /**
     * Applies the crypto state from the extracted backup.
     * @param extractResult The result from ExtractCryptoStateUseCase containing paths to extracted keystores
     * @return [ApplyCryptoStateResult.Success] if apply succeeded,
     * or [ApplyCryptoStateResult.Failure] if it failed.
     */
    suspend operator fun invoke(extractResult: ExtractCryptoStateResult.Success): ApplyCryptoStateResult
}

internal class ApplyCryptoStateUseCaseImpl(
    private val userId: UserId,
    private val rootPathsProvider: RootPathsProvider,
    private val kaliumFileSystem: KaliumFileSystem,
    private val passphraseStorage: PassphraseStorage,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : ApplyCryptoStateUseCase {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun invoke(extractResult: ExtractCryptoStateResult.Success): ApplyCryptoStateResult =
        withContext(dispatcher.io) {
            kaliumLogger.i("$TAG: Starting crypto state apply for userId: ${userId.toLogString()}")
            val clientId = extractResult.metadata.clientId

            try {
                // Apply MLS keystore
                val mlsApplyResult = applyMLSKeystore(
                    extractedMlsPath = extractResult.mlsKeystorePath,
                    clientId = clientId
                )
                if (mlsApplyResult is ApplyCryptoStateResult.Failure) {
                    return@withContext mlsApplyResult
                }

                // Apply Proteus keystore
                val proteusApplyResult = applyProteusKeystore(
                    extractedProteusPath = extractResult.proteusKeystorePath
                )
                if (proteusApplyResult is ApplyCryptoStateResult.Failure) {
                    return@withContext proteusApplyResult
                }

                // Update database passphrases from backup metadata
                updatePassphrases(extractResult.metadata)

                // Cleanup extracted files
                deleteExtractedFolder(extractResult.extractedDir)

                kaliumLogger.i("$TAG: Successfully applied crypto state for clientId: ${clientId.obfuscateId()}")
                ApplyCryptoStateResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                kaliumLogger.e("$TAG: Failed to apply crypto state", e)
                ApplyCryptoStateResult.Failure(CoreFailure.Unknown(e))
            }
        }

    /**
     * Updates the MLS and Proteus database passphrases in PassphraseStorage
     * using the values from the backup metadata.
     */
    private fun updatePassphrases(metadata: CryptoStateBackupMetadata) {
        // Update MLS passphrase
        val mlsPassphraseKey = "${MLS_DB_PASSPHRASE_PREFIX_V2}_$userId"
        passphraseStorage.setPassphrase(mlsPassphraseKey, metadata.mlsDbPassphrase)
        kaliumLogger.i("$TAG: Updated MLS database passphrase")

        // Update Proteus passphrase
        val proteusPassphraseKey = "${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$userId"
        passphraseStorage.setPassphrase(proteusPassphraseKey, metadata.proteusDbPassphrase)
        kaliumLogger.i("$TAG: Updated Proteus database passphrase")
    }

    private fun applyMLSKeystore(extractedMlsPath: Path, clientId: String): ApplyCryptoStateResult {
        if (!kaliumFileSystem.exists(extractedMlsPath)) {
            kaliumLogger.e("$TAG: Extracted MLS keystore not found")
            return ApplyCryptoStateResult.Failure(StorageFailure.DataNotFound)
        }

        // MLS path: $rootPath/${userId.domain}/${userId.value}/mls/${clientId}/keystore
        val mlsRootPath = rootPathsProvider.rootMLSPath(userId)
        val mlsKeystoreDir = "$mlsRootPath/$clientId"
        val mlsKeystorePath = "$mlsKeystoreDir/$KEYSTORE_NAME".toPath()

        kaliumLogger.i("$TAG: Applying MLS keystore..")

        FileUtil.mkDirs(mlsKeystoreDir)

        // Delete existing keystore files (main db + WAL files)
        deleteKeystoreFiles(mlsKeystorePath)

        // Copy the extracted keystore to the target location
        kaliumFileSystem.copy(extractedMlsPath, mlsKeystorePath)

        kaliumLogger.i("$TAG: MLS keystore applied successfully")
        return ApplyCryptoStateResult.Success
    }

    private fun applyProteusKeystore(extractedProteusPath: Path): ApplyCryptoStateResult {
        if (!kaliumFileSystem.exists(extractedProteusPath)) {
            kaliumLogger.e("$TAG: Extracted Proteus keystore not found")
            return ApplyCryptoStateResult.Failure(StorageFailure.DataNotFound)
        }

        // Proteus path: $rootPath/${userId.domain}/${userId.value}/proteus/keystore
        val proteusRootPath = rootPathsProvider.rootProteusPath(userId)
        val proteusKeystorePath = "$proteusRootPath/$KEYSTORE_NAME".toPath()

        kaliumLogger.i("$TAG: Applying Proteus keystore..")

        FileUtil.mkDirs(proteusRootPath)

        // Delete existing keystore files (main db + WAL files)
        deleteKeystoreFiles(proteusKeystorePath)

        // Copy the extracted keystore to the target location
        kaliumFileSystem.copy(extractedProteusPath, proteusKeystorePath)

        kaliumLogger.i("$TAG: Proteus keystore applied successfully")
        return ApplyCryptoStateResult.Success
    }

    /**
     * Deletes the keystore and its associated WAL files (keystore-shm, keystore-wal).
     * These files are created by SQLite in WAL mode and must be deleted together
     * to ensure data consistency when applying.
     */
    private fun deleteKeystoreFiles(keystorePath: Path) {
        val shmPath = "${keystorePath}$WAL_SHM_SUFFIX".toPath()
        val walPath = "${keystorePath}$WAL_SUFFIX".toPath()

        // Delete main keystore file
        if (kaliumFileSystem.exists(keystorePath)) {
            kaliumFileSystem.delete(keystorePath)
            kaliumLogger.i("$TAG: Deleted existing keystore")
        }

        // Delete shared memory file
        if (kaliumFileSystem.exists(shmPath)) {
            kaliumFileSystem.delete(shmPath)
            kaliumLogger.i("$TAG: Deleted existing keystore-shm")
        }

        // Delete write-ahead log file
        if (kaliumFileSystem.exists(walPath)) {
            kaliumFileSystem.delete(walPath)
            kaliumLogger.i("$TAG: Deleted existing keystore-wal")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deleteExtractedFolder(directory: Path) {
        if (kaliumFileSystem.exists(directory)) {
            try {
                // Delete all contents first
                kaliumFileSystem.deleteContents(directory, mustExist = false)

                // Then delete the directory itself
                kaliumFileSystem.delete(directory, mustExist = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val TAG = "[ApplyCryptoStateUseCase]"
        private const val KEYSTORE_NAME = "keystore"
        private const val WAL_SUFFIX = "-wal"
        private const val WAL_SHM_SUFFIX = "-shm"
        const val MLS_DB_PASSPHRASE_PREFIX_V2 = "mls_db_secret_alias_v2"
        const val PROTEUS_DB_PASSPHRASE_PREFIX_V2 = "proteus_db_secret_alias_v2"
    }
}

public sealed class ApplyCryptoStateResult {
    public data object Success : ApplyCryptoStateResult()
    public data class Failure(val error: CoreFailure) : ApplyCryptoStateResult()
}
