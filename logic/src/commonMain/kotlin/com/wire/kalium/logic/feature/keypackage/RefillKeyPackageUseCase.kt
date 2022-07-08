package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold

sealed class RefillKeyPackagesResult {

    object Success : RefillKeyPackagesResult()
    class Failure(val failure: CoreFailure) : RefillKeyPackagesResult()

}

interface RefillKeyPackagesUseCase {

    suspend operator fun invoke(): RefillKeyPackagesResult

}

internal const val KEY_PACKAGE_LIMIT = 100
internal const val KEY_PACKAGE_THRESHOLD = 0.5

class RefillKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val clientRepository: ClientRepository
) : RefillKeyPackagesUseCase {
    override suspend operator fun invoke(): RefillKeyPackagesResult =

        clientRepository.currentClientId().flatMap { selfClientId ->
            keyPackageRepository.getAvailableKeyPackageCount(selfClientId)
                .flatMap {
                    if (needsRefill(it.count)) {
                        keyPackageRepository.uploadNewKeyPackages(selfClientId, amount = KEY_PACKAGE_LIMIT - it.count).flatMap {
                            Either.Right(Unit)
                        }
                    } else {
                        Either.Right(Unit)
                    }
                }
        }.fold({ failure ->
            RefillKeyPackagesResult.Failure(failure)
        }, {
            RefillKeyPackagesResult.Success
        })

    private fun needsRefill(keyPackageCount: Int): Boolean {
        return keyPackageCount < (KEY_PACKAGE_LIMIT * KEY_PACKAGE_THRESHOLD)
    }
}
