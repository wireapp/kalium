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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.nomadTrace
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logger.obfuscateId

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
    private val clientRepository: ClientRepository,
    private val upgradeCurrentSession: UpgradeCurrentSessionUseCase,
) : RestoreCryptoStateUseCase {

    @Suppress("ReturnCount")
    override suspend fun invoke(): RestoreCryptoStateResult {
        kaliumLogger.nomadTrace(stage = "restore.flow.start")
        val downloadResult = downloadCryptoState()
        if (downloadResult is DownloadCryptoStateResult.NoBackupAvailable) {
            kaliumLogger.i("$TAG No crypto state backup available to restore")
            kaliumLogger.nomadTrace(stage = "restore.flow.no_backup")
            return RestoreCryptoStateResult.NoBackupAvailable
        }
        if (downloadResult is DownloadCryptoStateResult.Failure) {
            kaliumLogger.e("$TAG Failed to download crypto state backup")
            kaliumLogger.nomadTrace(
                stage = "restore.flow.download_failure",
                fields = mapOf("error" to downloadResult.error)
            )
            return RestoreCryptoStateResult.Failure
        }

        val backupFilePath = (downloadResult as DownloadCryptoStateResult.Success).backupFilePath

        val extractResult = extractCryptoState(backupFilePath)
        if (extractResult is ExtractCryptoStateResult.Failure) {
            kaliumLogger.e("$TAG Failed to extract crypto state backup")
            kaliumLogger.nomadTrace(
                stage = "restore.flow.extract_failure",
                fields = mapOf("error" to extractResult.error)
            )
            return RestoreCryptoStateResult.Failure
        }

        val extractSuccess = extractResult as ExtractCryptoStateResult.Success
        kaliumLogger.nomadTrace(
            stage = "restore.flow.extracted",
            fields = mapOf(
                "metadataClientId" to extractSuccess.metadata.clientId.obfuscateId(),
                "metadataLastProcessedEventId" to extractSuccess.metadata.lastProcessedEventId
            )
        )

        if (applyCryptoState(extractSuccess) is ApplyCryptoStateResult.Failure) {
            kaliumLogger.e("$TAG Failed to apply crypto state backup")
            return RestoreCryptoStateResult.Failure
        }

        if (setLastDeviceId(extractSuccess.metadata.clientId) is SetLastDeviceIdResult.Failure) {
            kaliumLogger.e("$TAG Failed to set last device ID after restoring crypto state")
            kaliumLogger.nomadTrace(
                stage = "restore.flow.set_last_device_id.failure",
                fields = mapOf(
                    "metadataClientId" to extractSuccess.metadata.clientId.obfuscateId(),
                    "metadataLastProcessedEventId" to extractSuccess.metadata.lastProcessedEventId
                )
            )
            return RestoreCryptoStateResult.Failure
        }

        kaliumLogger.i("$TAG Last device ID set successfully after restoring crypto state")
        kaliumLogger.nomadTrace(
            stage = "restore.flow.set_last_device_id.success",
            fields = mapOf(
                "metadataClientId" to extractSuccess.metadata.clientId.obfuscateId(),
                "metadataLastProcessedEventId" to extractSuccess.metadata.lastProcessedEventId
            )
        )
        return upgradeSession(extractSuccess.metadata.clientId)
    }

    @Suppress("ReturnCount")
    private suspend fun upgradeSession(clientId: String): RestoreCryptoStateResult {
        val clientsResult = clientRepository.selfListOfClients()
        if (clientsResult is Either.Left) {
            kaliumLogger.e("$TAG Failed to fetch client list from backend")
            return RestoreCryptoStateResult.Failure
        }

        val clients = (clientsResult as Either.Right).value
        val restoredClient = clients.find { it.id.value == clientId }
        if (restoredClient == null) {
            kaliumLogger.e("$TAG Restored client ID not found in backend client list")
            return RestoreCryptoStateResult.Failure
        }

        val upgradeResult = upgradeCurrentSession(restoredClient.id)
            .flatMap { clientRepository.persistClientId(restoredClient.id) }
            .flatMap { clientRepository.persistClientHasConsumableNotifications(restoredClient.isAsyncNotificationsCapable) }

        return when (upgradeResult) {
            is Either.Right -> {
                kaliumLogger.i("$TAG Current session upgraded successfully")
                kaliumLogger.nomadTrace(
                    stage = "restore.flow.session_upgrade.success",
                    fields = mapOf("clientId" to restoredClient.id.value.obfuscateId())
                )
                RestoreCryptoStateResult.Success
            }

            is Either.Left -> {
                kaliumLogger.e("$TAG Failed to upgrade current session after restoring crypto state")
                RestoreCryptoStateResult.Failure
            }
        }
    }

    companion object {
        const val TAG = "[RestoreCryptoStateUseCase]"
    }
}

public sealed interface RestoreCryptoStateResult {
    public object Success : RestoreCryptoStateResult
    public object Failure : RestoreCryptoStateResult
    public object NoBackupAvailable : RestoreCryptoStateResult
}
