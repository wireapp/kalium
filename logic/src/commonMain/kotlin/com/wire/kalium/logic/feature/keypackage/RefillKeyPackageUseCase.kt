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
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal sealed class RefillKeyPackagesResult {

    internal data object Success : RefillKeyPackagesResult()
    internal data class Failure(val failure: CoreFailure) : RefillKeyPackagesResult()

}

/**
 * This use case will check if the number of key packages is below the minimum threshold and will
 * upload new key packages if needed.
 *
 * If no key packages were left, other clients could not add this client to new MLS groups. In that case
 * pending MLS conversations are flagged to be joined by external commit, before uploading, so the flag
 * survives a failed upload or a restart.
 *
 * Key packages are generated in batches, each in its own MLS transaction, and every batch is uploaded only
 * after its transaction has committed. The backend never gets a key package whose private keys could still
 * be rolled back, and no transaction is held while waiting on the network. If an upload fails, the batch
 * just stays unused in the local keystore. Because of the transactions, this use case must not be called
 * from inside another crypto transaction.
 */
internal interface RefillKeyPackagesUseCase {

    suspend operator fun invoke(): RefillKeyPackagesResult

}

internal class RefillKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val userConfigRepository: UserConfigRepository,
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
                flagPendingConversationsIfDepleted(it.count).flatMap { _ ->
                    if (keyPackageLimitsProvider.needsRefill(it.count)) {
                        kaliumLogger.i("Key packages: Refilling key packages...")
                        uploadInBatches(selfClientId, keyPackageLimitsProvider.missingKeyPackages(it.count))
                    } else {
                        kaliumLogger.i("Key packages: Refill not needed")
                        Either.Right(Unit)
                    }
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
            if (offset > 0) delay(BATCH_DELAY)
            transactionProvider.mlsTransaction("GenerateKeyPackages") { mlsContext ->
                wrapMLSRequest { mlsContext.generateKeyPackages(minOf(batchSize, amount - offset)) }
            }.flatMap { keyPackages -> keyPackageRepository.uploadKeyPackages(clientId, keyPackages) }
        }
    }

    private suspend fun flagPendingConversationsIfDepleted(availableCount: Int): Either<CoreFailure, Unit> =
        if (availableCount == 0) {
            kaliumLogger.i("Key packages: None left, pending MLS conversations will be joined by external commit")
            userConfigRepository.setShouldJoinPendingMLSConversations(true)
        } else {
            Either.Right(Unit)
        }

    private companion object {
        val BATCH_DELAY = 250.milliseconds
    }
}
