package com.wire.kalium.logic.feature.user

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.api.model.AssetMetadata
import com.wire.kalium.network.api.model.AssetRetentionType

interface UploadUserAvatarUseCase {
    suspend operator fun invoke(imageData: ByteArray): Either<CoreFailure, Unit>
}

class UploadUserAvatarUseCaseImpl(private val assetDataSource: AssetRepository) : UploadUserAvatarUseCase {

    override suspend operator fun invoke(imageData: ByteArray): Either<CoreFailure, Unit> = suspending {
        assetDataSource.uploadAsset(createMetadataForAvatar(imageData), imageData)
    }

    // TODO: look for a better place to create the metadata, maybe a repo function or mapper
    // TODO: research if mimetype for this usecase is fine to be fixed or have it calculated dynamically
    private fun createMetadataForAvatar(imageData: ByteArray) =
        AssetMetadata("image/jpeg", true, AssetRetentionType.ETERNAL, calcMd5(imageData))
}
