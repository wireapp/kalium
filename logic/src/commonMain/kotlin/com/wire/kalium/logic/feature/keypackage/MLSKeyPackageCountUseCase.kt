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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrElse

/**
 * This use case will return the current number of key packages.
 */
interface MLSKeyPackageCountUseCase {
    suspend operator fun invoke(fromAPI: Boolean = true): MLSKeyPackageCountResult
}

internal class MLSKeyPackageCountUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val userConfigRepository: UserConfigRepository
) : MLSKeyPackageCountUseCase {
    override suspend operator fun invoke(fromAPI: Boolean): MLSKeyPackageCountResult =
        when (fromAPI) {
            true -> validKeyPackagesCountFromAPI()
            false -> validKeyPackagesCountFromMLSClient()
        }

    private suspend fun validKeyPackagesCountFromAPI() = currentClientIdProvider().fold({
        MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
    }, { selfClient ->
        if (userConfigRepository.isMLSEnabled().getOrElse(false)) {
            keyPackageRepository.getAvailableKeyPackageCount(selfClient)
                .fold(
                    { MLSKeyPackageCountResult.Failure.NetworkCallFailure(it) },
                    { MLSKeyPackageCountResult.Success(selfClient, it.count, keyPackageLimitsProvider.needsRefill(it.count)) }
                )
        } else {
            MLSKeyPackageCountResult.Failure.NotEnabled
        }
    })

    private suspend fun validKeyPackagesCountFromMLSClient() =
        currentClientIdProvider().fold({
            MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
        }, { selfClient ->
            keyPackageRepository.validKeyPackageCount(selfClient).fold(
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
