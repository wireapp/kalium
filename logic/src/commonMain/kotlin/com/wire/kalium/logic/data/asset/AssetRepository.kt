package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO

interface AssetRepository {
    suspend fun uploadPublicAsset(uploadAssetData: UploadAssetData): Either<CoreFailure, UploadedAssetId>
    suspend fun saveUserPictureAsset(assetId: List<UserAssetId>): Either<CoreFailure, Unit>
}

internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetMapper: AssetMapper,
    private val assetDao: AssetDAO
) : AssetRepository {

    override suspend fun uploadPublicAsset(uploadAssetData: UploadAssetData): Either<NetworkFailure, UploadedAssetId> = suspending {
        wrapApiRequest {
            assetMapper.toMetadataApiModel(uploadAssetData).let { metaData ->
                assetApi.uploadAsset(metaData, uploadAssetData.data)
            }
        }.map { assetResponse ->
            val assetEntity = assetMapper.toDaoModel(uploadAssetData, assetResponse)
            assetDao.insertAsset(assetEntity)
            assetMapper.toDomainModel(assetResponse)
        }
    }

    override suspend fun saveUserPictureAsset(assetId: List<UserAssetId>): Either<CoreFailure, Unit> = suspending {
        assetDao.insertAssets(assetId.map { assetMapper.fromUserAssetIdToDaoModel(it) })
        return@suspending Either.Right(Unit)
    }
}
