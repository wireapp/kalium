package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.utils.isSuccessful

interface AssetRepository {
    suspend fun uploadPublicAsset(metadata: UploadAssetMetadata, byteArray: ByteArray): Either<CoreFailure, UploadAssetId>
}

internal class AssetDataSource(private val assetApi: AssetApi) : AssetRepository {
    override suspend fun uploadPublicAsset(metadata: UploadAssetMetadata, byteArray: ByteArray): Either<CoreFailure, UploadAssetId> {
        val uploadedAsset = assetApi.uploadAsset(metadata.toApiModel(calcMd5(byteArray)), byteArray)
        return if (!uploadedAsset.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            Either.Right(uploadedAsset.value.toDomainModel())
        }
    }
}
