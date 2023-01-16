package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

sealed class RefillKeyPackagesResult {

    object Success : RefillKeyPackagesResult()
    class Failure(val failure: CoreFailure) : RefillKeyPackagesResult()

}

/**
 * This use case will check if the number of key packages is below the minimum threshold and will
 * upload new key packages if needed.
 */
interface RefillKeyPackagesUseCase {

    suspend operator fun invoke(): RefillKeyPackagesResult

}

class RefillKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : RefillKeyPackagesUseCase {
    override suspend operator fun invoke(): RefillKeyPackagesResult = withContext(dispatcher.default) {
        currentClientIdProvider().flatMap { selfClientId ->
            keyPackageRepository.getAvailableKeyPackageCount(selfClientId)
                .flatMap {
                    if (keyPackageLimitsProvider.needsRefill(it.count)) {
                        kaliumLogger.i("Refilling key packages...")
                        val amount = keyPackageLimitsProvider.refillAmount()
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
}
