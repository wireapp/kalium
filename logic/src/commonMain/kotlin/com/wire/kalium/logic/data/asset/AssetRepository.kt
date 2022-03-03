package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import kotlinx.coroutines.flow.firstOrNull

interface AssetRepository {
    suspend fun uploadPublicAsset(uploadAssetData: UploadAssetData): Either<CoreFailure, UploadedAssetId>
    suspend fun downloadPublicAsset(assetKey: String): Either<CoreFailure, ByteArray>
    suspend fun savePublicAsset(assetKey: String, data: ByteArray): Either<CoreFailure, Unit>
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
            val assetEntity = assetMapper.fromUploadedAssetToDaoModel(uploadAssetData, assetResponse)
            assetDao.insertAsset(assetEntity)
            assetMapper.fromApiUploadResponseToDomainModel(assetResponse)
        }
    }

    override suspend fun downloadPublicAsset(assetKey: String): Either<CoreFailure, ByteArray> = suspending {
        val persistedAsset = assetDao.getAssetByKey(assetKey).firstOrNull()
        if (persistedAsset?.rawData != null) return@suspending Either.Right(persistedAsset.rawData!!)

        wrapApiRequest {
            assetApi.downloadAsset(assetKey, null)
        }.map { assetData ->
            savePublicAsset(assetKey, assetData)
            assetData
        }
    }

    override suspend fun savePublicAsset(assetKey: String, data: ByteArray): Either<CoreFailure, Unit> = suspending {
        assetDao.updateAsset(assetMapper.fromUpdatedDataToDaoModel(assetKey, data))
        return@suspending Either.Right(Unit)
    }

    override suspend fun saveUserPictureAsset(assetId: List<UserAssetId>): Either<CoreFailure, Unit> = suspending {
        // TODO: on next PR we should download immediately the asset data and persist it
        assetDao.insertAssets(assetId.map { assetMapper.fromUserAssetIdToDaoModel(it) })
        return@suspending Either.Right(Unit)
    }
}
