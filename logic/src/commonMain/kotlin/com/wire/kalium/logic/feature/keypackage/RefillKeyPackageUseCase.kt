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

package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger

sealed class RefillKeyPackagesResult {

    data object Success : RefillKeyPackagesResult()
    data class Failure(val failure: CoreFailure) : RefillKeyPackagesResult()

}

/**
 * This use case will check if the number of key packages is below the minimum threshold and will
 * upload new key packages if needed.
 */
interface RefillKeyPackagesUseCase {

    suspend operator fun invoke(): RefillKeyPackagesResult

}

internal class RefillKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
) : RefillKeyPackagesUseCase {
    override suspend operator fun invoke(): RefillKeyPackagesResult {
        val selfClientId = currentClientIdProvider().getOrElse {
            return RefillKeyPackagesResult.Failure(it)
        }

        return mlsClientProvider.getMLSClient().map { CipherSuite.fromTag(it.getDefaultCipherSuite()) }.flatMap { cipherSuite ->
            keyPackageRepository.getAvailableKeyPackageCount(selfClientId, cipherSuite)
        }.flatMap {
            kaliumLogger.i("Key packages: Found ${it.count} available key packages")
            if (keyPackageLimitsProvider.needsRefill(it.count)) {
                kaliumLogger.i("Key packages: Refilling key packages...")
                val amount = keyPackageLimitsProvider.refillAmount()
                keyPackageRepository.uploadNewKeyPackages(selfClientId, amount).flatMap {
                    Either.Right(Unit)
                }
            } else {
                kaliumLogger.i("Key packages: Refill not needed")
                Either.Right(Unit)
            }
        }.fold({ failure ->
            RefillKeyPackagesResult.Failure(failure)
        }, {
            RefillKeyPackagesResult.Success
        })
    }
}
