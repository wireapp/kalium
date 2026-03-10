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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Path
import okio.buffer
import okio.use

/**
 * Downloads the crypto state backup from the remote endpoint.
 */
@Mockable
public interface DownloadCryptoStateUseCase {
    /**
     * Downloads the crypto state backup.
     * @return [DownloadCryptoStateResult.Success] with the path to the downloaded backup file,
     * or [DownloadCryptoStateResult.Failure] if the download failed.
     */
    public suspend operator fun invoke(): DownloadCryptoStateResult
}

internal class DownloadCryptoStateUseCaseImpl(
    private val userId: UserId,
    private val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : DownloadCryptoStateUseCase {

    override suspend fun invoke(): DownloadCryptoStateResult = withContext(dispatchers.io) {
        val backupFileName = createBackupFileName()
        val backupFilePath = kaliumFileSystem.tempFilePath(backupFileName)

        try {
            kaliumFileSystem.sink(backupFilePath).use { sink ->
                cryptoStateBackupRemoteRepository.downloadCryptoState(sink).fold(
                    { error ->
                        kaliumLogger.e("Failed to download crypto state backup: $error")
                        kaliumFileSystem.delete(backupFilePath)
                        DownloadCryptoStateResult.Failure(error)
                    },
                    {
                        validateDownloadedFile(backupFilePath, backupFileName)
                    }
                )
            }
        } catch (e: Exception) {
            kaliumLogger.e("Exception during crypto state download", e)
            kaliumFileSystem.delete(backupFilePath)
            DownloadCryptoStateResult.Failure(CoreFailure.Unknown(e))
        }
    }

    private fun validateDownloadedFile(backupFilePath: Path, backupFileName: String): DownloadCryptoStateResult {
        if (!kaliumFileSystem.exists(backupFilePath)) {
            kaliumLogger.e("Downloaded file does not exist at $backupFilePath")
            return DownloadCryptoStateResult.Failure(StorageFailure.DataNotFound)
        }

        val fileSize = kaliumFileSystem.source(backupFilePath).buffer().use {
            it.readAll(okio.blackholeSink())
        }

        kaliumLogger.i("Downloaded crypto state backup to $backupFilePath (size: $fileSize bytes)")

        return if (fileSize == 0L) {
            kaliumLogger.i("No crypto state backup available on server (empty response)")
            kaliumFileSystem.delete(backupFilePath)
            DownloadCryptoStateResult.NoBackupAvailable
        } else {
            DownloadCryptoStateResult.Success(backupFilePath, backupFileName)
        }
    }

    private fun createBackupFileName(): String {
        val timeStamp = DateTimeUtil.currentSimpleDateTimeString()
        return "${CRYPTO_BACKUP_DOWNLOAD_PREFIX}_${userId}_${timeStamp.replace(":", "-")}.zip"
    }

    companion object {
        const val CRYPTO_BACKUP_DOWNLOAD_PREFIX = "crypto_backup_download"
    }
}

public sealed class DownloadCryptoStateResult {
    public data class Success(val backupFilePath: Path, val backupFileName: String) : DownloadCryptoStateResult()
    public data object NoBackupAvailable : DownloadCryptoStateResult()
    public data class Failure(val error: CoreFailure) : DownloadCryptoStateResult()
}
