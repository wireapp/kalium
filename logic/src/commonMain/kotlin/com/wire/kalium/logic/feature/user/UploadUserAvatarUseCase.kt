package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.ImageAsset
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.suspending

interface UploadUserAvatarUseCase {
    /**
     * Function allowing the upload of a user profile picture (avatar)
     * This first will upload the data as an asset and then will link this asset with the [User]
     *
     * @param imageData binary data of the actual jpeg picture
     * @return UploadAvatarResult with [UserAssetId] in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(imageData: ByteArray): UploadAvatarResult
}

internal class UploadUserAvatarUseCaseImpl(
    private val userDataSource: UserRepository,
    private val assetDataSource: AssetRepository
) : UploadUserAvatarUseCase {

    override suspend operator fun invoke(imageData: ByteArray): UploadAvatarResult = suspending {
        val mimeType = ImageAsset.JPEG
        assetDataSource.uploadAndPersistPublicAsset(mimeType, imageData)
            .flatMap { asset ->
                userDataSource.updateSelfUser(newAssetId = asset.key)
            }.fold({
                UploadAvatarResult.Failure(it)
            }) { updatedUser ->
                UploadAvatarResult.Success(updatedUser.completePicture!!)
            } // todo: remove old assets, non blocking this response, as will imply deleting locally and remotely
    }
}

sealed class UploadAvatarResult {
    class Success(val userAssetId: UserAssetId) : UploadAvatarResult()
    class Failure(val coreFailure: CoreFailure) : UploadAvatarResult()
}
