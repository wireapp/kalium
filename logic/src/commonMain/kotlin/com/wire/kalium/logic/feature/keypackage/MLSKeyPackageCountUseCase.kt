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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import io.mockative.Mockable

/**
 * This use case will return the current number of key packages.
 */
@Mockable
interface MLSKeyPackageCountUseCase {
    suspend operator fun invoke(fromAPI: Boolean = true): MLSKeyPackageCountResult
}

internal class MLSKeyPackageCountUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val userConfigRepository: UserConfigRepository,
    private val transactionProvider: CryptoTransactionProvider,
) : MLSKeyPackageCountUseCase {
    override suspend operator fun invoke(fromAPI: Boolean): MLSKeyPackageCountResult =
        when (fromAPI) {
            true -> validKeyPackagesCountFromAPI()
            false -> validKeyPackagesCountFromMLSClient()
        }

    @Suppress("ReturnCount")
    private suspend fun validKeyPackagesCountFromAPI(): MLSKeyPackageCountResult {
        val selfClientId = currentClientIdProvider().getOrElse {
            return MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
        }

        if (!userConfigRepository.isMLSEnabled().getOrElse(false)) {
            return MLSKeyPackageCountResult.Failure.NotEnabled
        }

        val cipherSuite = transactionProvider.mlsTransaction("getDefaultCipherSuite") {
            it.getDefaultCipherSuite().toModel().right()
        }
            .getOrElse { return MLSKeyPackageCountResult.Failure.Generic(it) }

        return keyPackageRepository.getAvailableKeyPackageCount(selfClientId, cipherSuite).fold(
            { MLSKeyPackageCountResult.Failure.NetworkCallFailure(it) },
            { MLSKeyPackageCountResult.Success(selfClientId, it.count, keyPackageLimitsProvider.needsRefill(it.count)) }
        )
    }

    private suspend fun validKeyPackagesCountFromMLSClient() =
        currentClientIdProvider().fold({
            MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
        }, { selfClient ->
            transactionProvider.mlsTransaction("MLSKeyPackageCount") {
                keyPackageRepository.validKeyPackageCount(it, selfClient)
            }.fold(
                {
                    MLSKeyPackageCountResult.Failure.Generic(it)
                }, { MLSKeyPackageCountResult.Success(selfClient, it, keyPackageLimitsProvider.needsRefill(it)) })
        })
}

sealed class MLSKeyPackageCountResult {
    data class Success(val clientId: ClientId, val count: Int, val needsRefill: Boolean) : MLSKeyPackageCountResult()

    sealed class Failure : MLSKeyPackageCountResult() {
        class NetworkCallFailure(val networkFailure: NetworkFailure) : Failure()
        class FetchClientIdFailure(val genericFailure: CoreFailure) : Failure()
        data object NotEnabled : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
