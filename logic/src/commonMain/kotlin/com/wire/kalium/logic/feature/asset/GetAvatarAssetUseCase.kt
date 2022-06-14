package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.fold
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

internal class GetAvatarAssetUseCaseImpl(private val assetDataSource: AssetRepository) : GetAvatarAssetUseCase {
    override suspend fun invoke(assetKey: UserAssetId): PublicAssetResult =
        assetDataSource.downloadPublicAsset(assetKey).fold({
            PublicAssetResult.Failure(it)
        }) {
            PublicAssetResult.Success(it)
        }

}

sealed class PublicAssetResult {
    class Success(val asset: Path) : PublicAssetResult()
    class Failure(val coreFailure: CoreFailure) : PublicAssetResult()
}
