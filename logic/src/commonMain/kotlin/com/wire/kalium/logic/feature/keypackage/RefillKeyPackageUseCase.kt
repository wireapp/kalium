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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.keypackage.MLSMembershipAuditRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier

internal sealed class RefillKeyPackagesResult {

    internal data class Success(
        val availableCountBeforeRefill: Int,
        val refilled: Boolean,
    ) : RefillKeyPackagesResult()
    internal data class Failure(val failure: CoreFailure) : RefillKeyPackagesResult()

}

/**
 * This use case will check if the number of key packages is below the minimum threshold and will
 * upload new key packages if needed.
 */
internal interface RefillKeyPackagesUseCase {

    suspend operator fun invoke(mlsContext: MlsCoreCryptoContext): RefillKeyPackagesResult

}

internal class RefillKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier,
    private val mlsMembershipAuditRepository: MLSMembershipAuditRepository,
) : RefillKeyPackagesUseCase {
    override suspend operator fun invoke(mlsContext: MlsCoreCryptoContext): RefillKeyPackagesResult {
        val selfClientId = currentClientIdProvider().getOrElse {
            return RefillKeyPackagesResult.Failure(it)
        }
        val cipherSuite = mlsContext.getDefaultCipherSuite().toModel()
        return keyPackageRepository.getAvailableKeyPackageCount(selfClientId, cipherSuite)
            .flatMap { keyPackageCount ->
                val availableCountBeforeRefill = keyPackageCount.count
                kaliumLogger.i("Key packages: Found $availableCountBeforeRefill available key packages")
                val persistAuditRequired = if (availableCountBeforeRefill == 0) {
                    mlsMembershipAuditRepository.markAuditRequired()
                } else {
                    Either.Right(Unit)
                }
                persistAuditRequired.flatMap {
                    if (keyPackageLimitsProvider.needsRefill(availableCountBeforeRefill)) {
                        kaliumLogger.i("Key packages: Refilling key packages...")
                        val amount = keyPackageLimitsProvider.refillAmount()
                        keyPackageRepository.uploadNewKeyPackages(mlsContext, selfClientId, amount)
                            .onSuccess { cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId) }
                            .map { RefillKeyPackagesResult.Success(availableCountBeforeRefill, refilled = true) }
                    } else {
                        kaliumLogger.i("Key packages: Refill not needed")
                        Either.Right(RefillKeyPackagesResult.Success(availableCountBeforeRefill, refilled = false))
                    }
                }
            }.fold({ failure ->
                RefillKeyPackagesResult.Failure(failure)
            }, { it })
    }
}
