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
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier

internal sealed class RefillKeyPackagesResult {

    internal data object Success : RefillKeyPackagesResult()
    internal data class Failure(val failure: CoreFailure) : RefillKeyPackagesResult()

}

/**
 * This use case will check if the number of key packages is below the minimum threshold and will
 * upload new key packages if needed.
 *
 * Each batch runs in its own MLS transaction, so this must not be called from inside another crypto transaction.
 */
internal interface RefillKeyPackagesUseCase {

    suspend operator fun invoke(): RefillKeyPackagesResult

}

internal class RefillKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier,
    private val transactionProvider: CryptoTransactionProvider,
) : RefillKeyPackagesUseCase {
    override suspend operator fun invoke(): RefillKeyPackagesResult {
        val selfClientId = currentClientIdProvider().getOrElse {
            return RefillKeyPackagesResult.Failure(it)
        }
        return transactionProvider.mlsTransaction("getDefaultCipherSuite") {
            it.getDefaultCipherSuite().toModel().right()
        }
            .flatMap { cipherSuite -> keyPackageRepository.getAvailableKeyPackageCount(selfClientId, cipherSuite) }
            .flatMap {
                kaliumLogger.i("Key packages: Found ${it.count} available key packages")
                val pool = keyPackageLimitsProvider.keyPackagePool()
                if (pool.needsRefill(it.count)) {
                    kaliumLogger.i("Key packages: Refilling key packages to ${pool.targetSize}...")
                    uploadInBatches(selfClientId, pool.missingKeyPackages(it.count))
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

    private suspend fun uploadInBatches(clientId: ClientId, amount: Int): Either<CoreFailure, Unit> {
        val batchSize = keyPackageLimitsProvider.refillAmount()
        return (0 until amount step batchSize).toList().foldToEitherWhileRight(Unit) { offset, _ ->
            transactionProvider.mlsTransaction("RefillKeyPackages") { mlsContext ->
                keyPackageRepository.uploadNewKeyPackages(mlsContext, clientId, minOf(batchSize, amount - offset))
            }.onSuccess { cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId) }
        }
    }
}
