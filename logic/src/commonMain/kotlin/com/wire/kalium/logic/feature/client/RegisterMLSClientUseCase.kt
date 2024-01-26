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

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

sealed class RegisterMLSClientResult {
    data object Success : RegisterMLSClientResult()

    data class E2EICertificateRequired(val mlsClient: MLSClient) : RegisterMLSClientResult()
}

/**
 * Register an MLS client with an existing client already registered on the backend.
 */
interface RegisterMLSClientUseCase {

    suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, RegisterMLSClientResult>
}

internal class RegisterMLSClientUseCaseImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val clientRepository: ClientRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val userConfigRepository: UserConfigRepository
) : RegisterMLSClientUseCase {

    override suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, RegisterMLSClientResult> =
        mlsClientProvider.getMLSClient(clientId).flatMap { mlsClient ->
            userConfigRepository.getE2EISettings().fold({
                Either.Right(mlsClient)
            }, { e2eiSettings ->
                if (e2eiSettings.isRequired && !mlsClient.isE2EIEnabled()) {
                    kaliumLogger.i("MLS Client registration stopped: e2ei is required and is not enrolled!")
                    return Either.Right(RegisterMLSClientResult.E2EICertificateRequired(mlsClient))
                } else Either.Right(mlsClient)
            })
        }.flatMap {
            clientRepository.registerMLSClient(clientId, it.getPublicKey())
        }.flatMap {
            keyPackageRepository.uploadNewKeyPackages(clientId, keyPackageLimitsProvider.refillAmount())
            Either.Right(RegisterMLSClientResult.Success)
        }
}
