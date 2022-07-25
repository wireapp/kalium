package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.ImageAsset
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
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
    private val assetDataSource: AssetRepository
) : UploadUserAvatarUseCase {

    override suspend operator fun invoke(imageDataPath: Path, imageDataSize: Long): UploadAvatarResult {
        return assetDataSource.uploadAndPersistPublicAsset(ImageAsset.JPEG, imageDataPath, imageDataSize).flatMap { asset ->
            userDataSource.updateSelfUser(newAssetId = asset.key)
        }.fold({
            UploadAvatarResult.Failure(it)
        }) { updatedUser ->
            UploadAvatarResult.Success(updatedUser.completePicture!!)
        } // TODO(assets): remove old assets, non blocking this response, as will imply deleting locally and remotely
    }
}

sealed class UploadAvatarResult {
    class Success(val userAssetId: UserAssetId) : UploadAvatarResult()
    class Failure(val coreFailure: CoreFailure) : UploadAvatarResult()
}
