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
    suspend fun uploadAndPersistPublicAsset(uploadAssetData: UploadAssetData): Either<CoreFailure, UploadedAssetId>
    suspend fun saveUserPictureAsset(assetId: List<UserAssetId>): Either<CoreFailure, Unit>
}

internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetMapper: AssetMapper,
    private val assetDao: AssetDAO
) : AssetRepository {

    override suspend fun uploadAndPersistPublicAsset(uploadAssetData: UploadAssetData): Either<NetworkFailure, UploadedAssetId> = suspending {
        wrapApiRequest {
            assetMapper.toMetadataApiModel(uploadAssetData).let { metaData ->
                assetApi.uploadAsset(metaData, uploadAssetData.data)
            }
        }.map { assetResponse ->
            val assetEntity = assetMapper.fromUploadedAssetToDaoModel(uploadAssetData, assetResponse)
            assetDao.insertAsset(assetEntity)
            assetMapper.fromApiUploadResponseToDomainModel(assetResponse)
        }
    }

    override suspend fun saveUserPictureAsset(assetId: List<UserAssetId>): Either<CoreFailure, Unit> = suspending {
        // TODO: on next PR we should download immediately the asset data and persist it
        assetDao.insertAssets(assetId.map { assetMapper.fromUserAssetIdToDaoModel(it) })
        return@suspending Either.Right(Unit)
    }
}
