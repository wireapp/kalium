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

class RegisterMLSClientUseCaseImpl(
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
