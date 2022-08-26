package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

sealed class RefillKeyPackagesResult {

    object Success : RefillKeyPackagesResult()
    class Failure(val failure: CoreFailure) : RefillKeyPackagesResult()

}

interface RefillKeyPackagesUseCase {

    suspend operator fun invoke(): RefillKeyPackagesResult

}

class RefillKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val clientRepository: ClientRepository,
) : RefillKeyPackagesUseCase {
    override suspend operator fun invoke(): RefillKeyPackagesResult =
        clientRepository.currentClientId().flatMap { selfClientId ->
            keyPackageRepository.getAvailableKeyPackageCount(selfClientId)
                .flatMap {
                    if (keyPackageLimitsProvider.needsRefill(it.count)) {
                        kaliumLogger.i("Refilling key packages...")
                        val amount = keyPackageLimitsProvider.refillAmount(it.count)
                        keyPackageRepository.uploadNewKeyPackages(selfClientId, amount).flatMap {
                            Either.Right(Unit)
                        }
                    } else {
                        kaliumLogger.i("Key packages didn't need refilling")
                        Either.Right(Unit)
                    }
                }
        }.fold({ failure ->
            RefillKeyPackagesResult.Failure(failure)
        }, {
            RefillKeyPackagesResult.Success
        })

}
