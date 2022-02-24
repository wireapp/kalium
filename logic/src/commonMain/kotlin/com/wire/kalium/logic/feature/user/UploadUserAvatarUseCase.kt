package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.ImageAsset
import com.wire.kalium.logic.data.asset.RetentionType
import com.wire.kalium.logic.data.asset.UploadAssetData
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

interface UploadUserAvatarUseCase {
    /**
     * Function allowing the upload of a user profile picture (avatar)
     * This first will upload the data as an asset and then will link this asset with the [User]
     *
     * @param mimeType mimetype of the user picture
     * @param imageData binary data of the actual picture
     */
    suspend operator fun invoke(mimeType: String, imageData: ByteArray): Either<CoreFailure, Unit>
}

class UploadUserAvatarUseCaseImpl(
    private val userDataSource: UserRepository,
    private val assetDataSource: AssetRepository
) : UploadUserAvatarUseCase {

    override suspend operator fun invoke(mimeType: String, imageData: ByteArray): Either<CoreFailure, Unit> = suspending {
        assetDataSource
            .uploadPublicAsset(UploadAssetData(imageData, ImageAsset.JPG, true, RetentionType.ETERNAL))
            .map { asset ->
                userDataSource.updateSelfUser(newAssetId = asset.key)
            }

        return@suspending Either.Right(Unit)
    }
}
