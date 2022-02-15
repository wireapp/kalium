package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.RetentionType
import com.wire.kalium.logic.data.asset.UploadAssetMetadata
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

interface UploadUserAvatarUseCase {
    suspend operator fun invoke(mimeType: String, imageData: ByteArray): Either<CoreFailure, Unit>
}

class UploadUserAvatarUseCaseImpl(
    private val userDataSource: UserRepository,
    private val assetDataSource: AssetRepository
) : UploadUserAvatarUseCase {

    override suspend operator fun invoke(mimeType: String, imageData: ByteArray): Either<CoreFailure, Unit> = suspending {
        assetDataSource
            .uploadPublicAsset(UploadAssetMetadata(mimeType, true, RetentionType.ETERNAL), imageData)
        // .flatMap {}

        return@suspending Either.Right(Unit)
    }
}
