package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.suspending

interface GetPublicAssetUseCase {
    /**
     * Function that enables downloading a public asset by its asset-key, mostly used for avatar pictures
     * Internally, if the asset doesn't exist locally, this will download it first and then return it
     *
     * @param assetKey the asset identifier
     * @return PublicAssetResult with a [ByteArray] in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(assetKey: UserAssetId): PublicAssetResult
}

internal class GetPublicAssetUseCaseImpl(private val assetDataSource: AssetRepository) : GetPublicAssetUseCase {
    override suspend fun invoke(assetKey: UserAssetId): PublicAssetResult = suspending {
        assetDataSource.downloadPublicAsset(assetKey).fold({
            PublicAssetResult.Failure(it)
        }) {
            PublicAssetResult.Success(it)
        }
    }
}

sealed class PublicAssetResult {
    class Success(val asset: ByteArray) : PublicAssetResult()
    class Failure(val coreFailure: CoreFailure) : PublicAssetResult()
}
