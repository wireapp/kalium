package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.model.AssetMetadata
import com.wire.kalium.network.utils.isSuccessful

interface AssetRepository {
    suspend fun uploadAsset(assetMetadata: AssetMetadata, byteArray: ByteArray): Either<CoreFailure, Unit>
}

internal class AssetDataSource(private val assetApi: AssetApi) : AssetRepository {
    override suspend fun uploadAsset(assetMetadata: AssetMetadata, byteArray: ByteArray): Either<CoreFailure, Unit> {
        val uploadedAsset = assetApi.uploadAsset(assetMetadata, byteArray)
        return if (!uploadedAsset.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            Either.Right(Unit)
        }
    }
}
