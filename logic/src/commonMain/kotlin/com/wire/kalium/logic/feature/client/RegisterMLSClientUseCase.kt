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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap

/**
 * Register an MLS client with an existing client already registered on the backend.
 */
interface RegisterMLSClientUseCase {

    suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, Unit>
}

internal class RegisterMLSClientUseCaseImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val clientRepository: ClientRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
) : RegisterMLSClientUseCase {

    override suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient(clientId)
            .flatMap { clientRepository.registerMLSClient(clientId, it.getPublicKey()) }
            .flatMap { keyPackageRepository.uploadNewKeyPackages(clientId, keyPackageLimitsProvider.refillAmount()) }
}
