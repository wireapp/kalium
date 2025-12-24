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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.nullableFold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.feature.backup.DownloadAndRestoreCryptoStateResult
import com.wire.kalium.logic.feature.backup.DownloadAndRestoreCryptoStateUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import io.mockative.Mockable

/**
 * This use case is responsible for getting the client.
 * If the client is not found, it will be registered.
 */
@Mockable
interface GetOrRegisterClientUseCase {
    suspend operator fun invoke(
        registerClientParam: RegisterClientParam
    ): RegisterClientResult
}

@Suppress("LongParameterList")
internal class GetOrRegisterClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val logoutRepository: LogoutRepository,
    private val registerClient: RegisterClientUseCase,
    private val clearClientData: ClearClientDataUseCase,
    private val verifyExistingClientUseCase: VerifyExistingClientUseCase,
    private val upgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase,
    private val cachedClientIdClearer: CachedClientIdClearer,
    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase,
    private val downloadAndRestoreCryptoState: DownloadAndRestoreCryptoStateUseCase,
    private val kaliumConfigs: KaliumConfigs
) : GetOrRegisterClientUseCase {

    override suspend fun invoke(registerClientParam: RegisterClientParam): RegisterClientResult {
        syncFeatureConfigsUseCase()

        val result: RegisterClientResult = clientRepository.retainedClientId()
            .nullableFold(
                {
                    if (it is CoreFailure.MissingClientRegistration) null
                    else RegisterClientResult.Failure.Generic(it)
                }, { retainedClientId ->
                    when (val result = verifyExistingClientUseCase(retainedClientId)) {
                        is VerifyExistingClientResult.Success -> RegisterClientResult.Success(result.client)
                        is VerifyExistingClientResult.Failure.Generic -> RegisterClientResult.Failure.Generic(result.genericFailure)
                        is VerifyExistingClientResult.Failure.ClientNotRegistered -> {
                            clearOldClientRelatedData()
                            null
                        }

                        is VerifyExistingClientResult.Failure.E2EICertificateRequired -> RegisterClientResult.E2EICertificateRequired(
                            result.client,
                            result.userId
                        )
                    }
                }
            ) ?: tryRestoreFromBackupOrRegister(registerClientParam)

        when (result) {
            is RegisterClientResult.E2EICertificateRequired -> {
                kaliumLogger.i("Client registration blocked because E2EI certificate required")
                clientRepository.setClientRegistrationBlockedByE2EI()
                upgradeCurrentSessionAndPersistClient(result.client.id)
            }

            is RegisterClientResult.Success ->
                upgradeCurrentSessionAndPersistClient(
                    result.client.id,
                    result.client.isAsyncNotificationsCapable
                )

            else -> Unit
        }

        return result
    }

    private suspend fun upgradeCurrentSessionAndPersistClient(clientId: ClientId, isConsumableNotificationsCapable: Boolean = false) {
        kaliumLogger.i("Upgrade current session for client ${clientId.value.obfuscateId()}")
        upgradeCurrentSessionUseCase(clientId).flatMap {
            kaliumLogger.i("Persist client ${clientId.value.obfuscateId()}")
            clientRepository.persistClientId(clientId)
            clientRepository.persistClientHasConsumableNotifications(isConsumableNotificationsCapable)
        }
    }

    private suspend fun tryRestoreFromBackupOrRegister(registerClientParam: RegisterClientParam): RegisterClientResult {
        // Check if crypto state backup feature is enabled and remote backup URL is configured
        if (kaliumConfigs.cryptoStateBackupEnabled) {
            kaliumLogger.i("Crypto state backup enabled and remote backup URL configured, checking for crypto state backup")

            when (val restoreResult = downloadAndRestoreCryptoState()) {
                is DownloadAndRestoreCryptoStateResult.Success -> {
                    kaliumLogger.i("Successfully restored crypto state from backup, client ID: ${restoreResult.clientId.value.obfuscateId()}")

                    // Fetch all self user clients from backend and find the restored one
                    val clientListResult = clientRepository.selfListOfClients()

                    return when (clientListResult) {
                        is Either.Left -> {
                            kaliumLogger.e("Failed to fetch client list from backend: ${clientListResult.value}")
                            // If we can't fetch the client list, fall back to registration
                            registerClient(registerClientParam)
                        }
                        is Either.Right -> {
                            // Find the client matching the restored client ID
                            val restoredClient = clientListResult.value.find { it.id == restoreResult.clientId }
                            if (restoredClient != null) {
                                kaliumLogger.i("Retrieved restored client details")
                                RegisterClientResult.Success(restoredClient)
                            } else {
                                kaliumLogger.w("Restored client ID not found in backend client list, falling back to registration")
                                registerClient(registerClientParam)
                            }
                        }
                    }
                }

                is DownloadAndRestoreCryptoStateResult.NoBackupFound -> {
                    kaliumLogger.i("No backup found, proceeding with normal client registration")
                    // No backup exists, proceed with normal registration
                    return registerClient(registerClientParam)
                }

                is DownloadAndRestoreCryptoStateResult.Failure -> {
                    kaliumLogger.w("Failed to restore from backup: ${restoreResult.error}, proceeding with normal registration")
                    // Error during restore, proceed with normal registration
                    return registerClient(registerClientParam)
                }
            }
        } else {
            // Feature disabled, proceed with normal registration
            return registerClient(registerClientParam)
        }
    }

    private suspend fun clearOldClientRelatedData() {
        cachedClientIdClearer()
        clearClientData()
        logoutRepository.clearClientRelatedLocalMetadata()
        clientRepository.clearRetainedClientId()
        clientRepository.clearClientHasConsumableNotifications()
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)
    }
}
