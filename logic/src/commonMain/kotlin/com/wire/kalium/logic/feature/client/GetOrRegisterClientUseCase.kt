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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.nullableFold

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
class GetOrRegisterClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val logoutRepository: LogoutRepository,
    private val registerClient: RegisterClientUseCase,
    private val clearClientData: ClearClientDataUseCase,
    private val verifyExistingClientUseCase: VerifyExistingClientUseCase,
    private val upgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase,
    private val cachedClientIdClearer: CachedClientIdClearer
) : GetOrRegisterClientUseCase {

    override suspend fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult {
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
                    }
                }
            ) ?: registerClient(registerClientParam)

        if (result is RegisterClientResult.Success) {
            upgradeCurrentSessionUseCase(result.client.id).flatMap {
                clientRepository.persistClientId(result.client.id)
            }
        }

        return result
    }

    private suspend fun clearOldClientRelatedData() {
        cachedClientIdClearer()
        clearClientData()
        logoutRepository.clearClientRelatedLocalMetadata()
        clientRepository.clearRetainedClientId()
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)
    }
}
