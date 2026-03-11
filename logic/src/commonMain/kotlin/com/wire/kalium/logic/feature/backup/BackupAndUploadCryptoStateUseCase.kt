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
@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import okio.blackholeSink
import okio.buffer
import okio.use

/**
 * Creates a crypto state backup and uploads it to the remote endpoint.
 */
public interface BackupAndUploadCryptoStateUseCase {
    /**
     * Creates and uploads the crypto state backup.
     */
    public suspend operator fun invoke(): BackupAndUploadCryptoStateResult
}

internal class BackupAndUploadCryptoStateUseCaseImpl(
    private val backupCryptoDBUseCase: BackupCryptoDBUseCase,
    private val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val currentClientIdProvider: CurrentClientIdProvider,
) : BackupAndUploadCryptoStateUseCase {
    override suspend fun invoke(): BackupAndUploadCryptoStateResult =
        when (val backupResult = backupCryptoDBUseCase.invoke()) {
            is BackupCryptoDBResult.Success -> {
                val clientId = when (val clientResult = currentClientIdProvider.invoke()) {
                    is Either.Left -> {
                        kaliumLogger.e("Failed to read current client id ${clientResult.value}")
                        return BackupAndUploadCryptoStateResult.Failure(clientResult.value)
                    }

                    is Either.Right -> clientResult.value
                }
                val backupSize = kaliumFileSystem.source(backupResult.backupFilePath).use { source ->
                    source.buffer().readAll(blackholeSink())
                }
                val uploadResult = cryptoStateBackupRemoteRepository.uploadCryptoState(
                    clientId = clientId.value,
                    sourceProvider = { kaliumFileSystem.source(backupResult.backupFilePath) },
                    size = backupSize
                ).mapLeft { error ->
                    kaliumLogger.e("Failed to upload crypto state backup")
                    error
                }
                when (uploadResult) {
                    is Either.Left ->
                        BackupAndUploadCryptoStateResult.Failure(uploadResult.value)

                    is Either.Right ->
                        BackupAndUploadCryptoStateResult.Success
                }
            }

            is BackupCryptoDBResult.Failure -> {
                kaliumLogger.e("Failed to create crypto state backup ${backupResult.error}")
                BackupAndUploadCryptoStateResult.Failure(backupResult.error)
            }
        }
}

public sealed class BackupAndUploadCryptoStateResult {
    public data object Success : BackupAndUploadCryptoStateResult()
    public data class Failure(public val error: CoreFailure) : BackupAndUploadCryptoStateResult()
}
