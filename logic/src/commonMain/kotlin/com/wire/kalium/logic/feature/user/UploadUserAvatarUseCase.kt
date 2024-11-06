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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.Path

interface UploadUserAvatarUseCase {
    /**
     * Function allowing the upload of a user profile picture (avatar)
     * This first will upload the data as an asset and then will link this asset with the [User]
     *
     * @param imageDataPath data [Path] of the actual picture
     * @return UploadAvatarResult with [UserAssetId] in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(imageDataPath: Path, imageDataSize: Long): UploadAvatarResult
}

internal class UploadUserAvatarUseCaseImpl(
    private val userDataSource: UserRepository,
    private val assetDataSource: AssetRepository,
    private val dispatcher: KaliumDispatcherImpl = KaliumDispatcherImpl
) : UploadUserAvatarUseCase {

    override suspend operator fun invoke(imageDataPath: Path, imageDataSize: Long): UploadAvatarResult {
        return withContext(dispatcher.io) {
            assetDataSource.uploadAndPersistPublicAsset("image/jpg", imageDataPath, imageDataSize).flatMap { asset ->
                userDataSource.updateSelfUser(newAssetId = asset.key).map { asset }
            }.fold({
                UploadAvatarResult.Failure(it)
            }) { updatedAsset ->
                UploadAvatarResult.Success(UserAssetId(updatedAsset.key, updatedAsset.domain))
            } // TODO(assets): remove old assets, non blocking this response, as will imply deleting locally and remotely
        }
    }
}

sealed class UploadAvatarResult {
    class Success(val userAssetId: UserAssetId) : UploadAvatarResult()
    class Failure(val coreFailure: CoreFailure) : UploadAvatarResult()
}
