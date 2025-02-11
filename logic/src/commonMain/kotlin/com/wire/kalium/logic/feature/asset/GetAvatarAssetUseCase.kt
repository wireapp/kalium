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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.fold
import okio.Path

interface GetAvatarAssetUseCase {
    /**
     * Function that enables downloading a public asset by its asset-key, mostly used for avatar pictures
     * Internally, if the asset doesn't exist locally, this will download it first and then return it
     *
     * @param assetKey the asset identifier
     * @return PublicAssetResult with a [ByteArray] in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(assetKey: UserAssetId): PublicAssetResult
}

internal class GetAvatarAssetUseCaseImpl(
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository
) : GetAvatarAssetUseCase {
    override suspend fun invoke(assetKey: UserAssetId): PublicAssetResult =
        // TODO(important!!): do local lookup for the profile pic before downloading a new one
        assetDataSource.downloadPublicAsset(assetKey.value, assetKey.domain).fold({
            when {
                it.isInvalidRequestError -> {
                    userRepository.removeUserBrokenAsset(assetKey)
                    PublicAssetResult.Failure(it, false)
                }

                it is NetworkFailure.FederatedBackendFailure -> PublicAssetResult.Failure(it, false)
                else -> PublicAssetResult.Failure(it, true)
            }
        }) {
            PublicAssetResult.Success(it)
        }
}

sealed class PublicAssetResult {
    class Success(val assetPath: Path) : PublicAssetResult()
    class Failure(val coreFailure: CoreFailure, val isRetryNeeded: Boolean) : PublicAssetResult()
}
