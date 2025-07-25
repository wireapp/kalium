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
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import io.mockative.Mockable

sealed class RegisterMLSClientResult {
    data object Success : RegisterMLSClientResult()

    data object E2EICertificateRequired : RegisterMLSClientResult()
}

/**
 * Register an MLS client with an existing client already registered on the backend.
 */
@Mockable
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

    override suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, RegisterMLSClientResult> {
        return userConfigRepository.getE2EISettings().flatMap { e2eiSettings ->
            if (e2eiSettings.isRequired && !mlsClientProvider.isMLSClientInitialised()) {
                return RegisterMLSClientResult.E2EICertificateRequired.right()
            } else {
                mlsClientProvider.getMLSClient(clientId)
            }
        }.onFailure {
            mlsClientProvider.getMLSClient(clientId)
        }.flatMap { mlsClient ->
            wrapMLSRequest { mlsClient.getPublicKey() }
                .flatMap { (publicKey, cipherSuite) ->
                    clientRepository.registerMLSClient(clientId, publicKey, cipherSuite.toModel())
                }.flatMap {
                    mlsClient.transaction("uploadNewKeyPackages") { context ->
                        keyPackageRepository.uploadNewKeyPackages(context, clientId, keyPackageLimitsProvider.refillAmount())
                    }
                        .map { RegisterMLSClientResult.Success }
                }
        }.onFailure {
            kaliumLogger.e("Failed to register MLS client: $it")
        }
    }
}
