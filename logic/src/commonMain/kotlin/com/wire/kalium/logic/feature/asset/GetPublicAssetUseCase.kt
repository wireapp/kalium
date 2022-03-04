package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.suspending

interface GetPublicAssetUseCase {
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
