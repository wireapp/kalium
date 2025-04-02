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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.nullableFold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase

/**
 * This use case is responsible for getting the client.
 * If the client is not found, it will be registered.
 */
interface GetOrRegisterClientUseCase {
    suspend operator fun invoke(
        registerClientParam: RegisterClientUseCase.RegisterClientParam
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
    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
) : GetOrRegisterClientUseCase {

    override suspend fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult {
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
            ) ?: registerClient(registerClientParam)

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
            // todo (ym) also persist the capability in metadata [isConsumableNotificationsAble]...
            clientRepository.persistClientId(clientId)
        }
    }

    private suspend fun clearOldClientRelatedData() {
        cachedClientIdClearer()
        clearClientData()
        logoutRepository.clearClientRelatedLocalMetadata()
        clientRepository.clearRetainedClientId()
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)
    }
}
