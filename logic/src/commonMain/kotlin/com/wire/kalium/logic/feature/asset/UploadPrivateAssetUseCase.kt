package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetType
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

interface UploadPrivateAssetUseCase {
    /**
     * Function allowing the upload of a user profile picture (avatar)
     * This first will upload the data as an asset and then will link this asset with the [User]
     *
     * @param mimeType mime type of the asset to be sent
     * @param assetEncryptedData binary data of the actual asset to be sent
     * @return an [Either] tuple containing [CoreFailure] in case of an error or the [UploadedAssetId] containing the identifier of the
     * successfully uploaded asset
     */
    suspend operator fun invoke(mimeType: AssetType, assetEncryptedData: ByteArray): Either<CoreFailure, UploadedAssetId>
}

internal class UploadPrivateAssetUseCaseImpl(
    private val userDataSource: UserRepository,
    private val assetDataSource: AssetRepository
) : UploadPrivateAssetUseCase {

    override suspend operator fun invoke(mimeType: AssetType, assetEncryptedData: ByteArray): Either<CoreFailure, UploadedAssetId> =
        suspending {
            val uploadResult = assetDataSource.uploadAndPersistPrivateAsset(mimeType, assetEncryptedData)
            uploadResult.flatMap { asset -> userDataSource.updateSelfUser(newAssetId = asset.key) }
            uploadResult
        }
}
