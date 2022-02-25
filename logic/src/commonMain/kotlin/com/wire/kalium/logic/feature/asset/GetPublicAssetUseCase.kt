package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

interface GetPublicAssetUseCase {
    suspend operator fun invoke(assetKey: String): Either<CoreFailure, ByteArray>
}

internal class GetAssetUseCaseImpl(private val assetDataSource: AssetRepository) : GetPublicAssetUseCase {
    override suspend fun invoke(assetKey: String): Either<CoreFailure, ByteArray> = suspending {
        return@suspending assetDataSource.downloadPublicAsset(assetKey)
    }
}
