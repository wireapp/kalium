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

public interface RestoreCryptoStateUseCase {

    /**
     * Executes the restore flow for the crypto state backup.
     * This includes downloading the backup, validating it, and restoring the crypto state.
     * @return [RestoreCryptoStateResult.Success] if the restore flow succeeded,
     * [RestoreCryptoStateResult.Failure] if the restore flow failed due to login issues,
     * or [RestoreCryptoStateResult.NoBackupAvailable] if no backup was available to restore.
     */
    public suspend operator fun invoke(): RestoreCryptoStateResult
}

internal class RestoreCryptoStateUseCaseImpl(
    private val downloadCryptoState: DownloadCryptoStateUseCase,
    private val extractCryptoState: ExtractCryptoStateUseCase,
    private val setLastDeviceId: SetLastDeviceIdUseCase,
    private val applyCryptoState: ApplyCryptoStateUseCase,
) : RestoreCryptoStateUseCase {
    override suspend fun invoke(): RestoreCryptoStateResult {
        when (val downloadResult = downloadCryptoState()) {
            is DownloadCryptoStateResult.Success -> {
                val backupFilePath = downloadResult.backupFilePath

                when (val extractResult = extractCryptoState(backupFilePath)) {
                    is ExtractCryptoStateResult.Success -> {
                        return when (applyCryptoState(extractResult)) {
                            is ApplyCryptoStateResult.Success -> {
                                when (setLastDeviceId()) {
                                    is SetLastDeviceIdResult.Success -> RestoreCryptoStateResult.Success
                                    is SetLastDeviceIdResult.Failure -> RestoreCryptoStateResult.Failure
                                }
                            }

                            is ApplyCryptoStateResult.Failure -> RestoreCryptoStateResult.Failure
                        }
                    }

                    is ExtractCryptoStateResult.Failure -> return RestoreCryptoStateResult.Failure
                }
            }

            DownloadCryptoStateResult.NoBackupAvailable -> return RestoreCryptoStateResult.NoBackupAvailable
            is DownloadCryptoStateResult.Failure -> return RestoreCryptoStateResult.Failure
        }
    }
}

public sealed interface RestoreCryptoStateResult {
    public object Success : RestoreCryptoStateResult
    public object Failure : RestoreCryptoStateResult
    public object NoBackupAvailable : RestoreCryptoStateResult
}
