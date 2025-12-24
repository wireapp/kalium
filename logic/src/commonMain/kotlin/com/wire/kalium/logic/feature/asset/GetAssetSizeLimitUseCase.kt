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

import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCase.AssetSizeLimits
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

public interface GetAssetSizeLimitUseCase {
    /**
     * Returns the maximum size in Bytes of an asset that can be uploaded to the Wire backend.
     * @param isImage whether the asset to upload is an image or not
     */
    public suspend operator fun invoke(isImage: Boolean): Long

    public object AssetSizeLimits {
        public const val IMAGE_SIZE_LIMIT_BYTES: Long = 15L * 1024 * 1024 // 15 MB limit for images
        public const val ASSET_SIZE_DEFAULT_LIMIT_BYTES: Long = 25L * 1024 * 1024 // 25 MB asset default user limit size
        public const val ASSET_SIZE_TEAM_USER_LIMIT_BYTES: Long = 100L * 1024 * 1024 // 100 MB asset team user limit size
    }
}

internal class GetAssetSizeLimitUseCaseImpl internal constructor(
    private val isSelfATeamMember: IsSelfATeamMemberUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetAssetSizeLimitUseCase {
    override suspend operator fun invoke(isImage: Boolean): Long = withContext(dispatchers.default) {
        val hasUserTeam = isSelfATeamMember()
        return@withContext when {
            isImage -> AssetSizeLimits.IMAGE_SIZE_LIMIT_BYTES
            hasUserTeam -> AssetSizeLimits.ASSET_SIZE_TEAM_USER_LIMIT_BYTES
            else -> AssetSizeLimits.ASSET_SIZE_DEFAULT_LIMIT_BYTES
        }
    }
}
