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
    suspend fun uploadAndPersistPublicAsset(uploadAssetData: UploadAssetData): Either<CoreFailure, UploadedAssetId>
    suspend fun downloadPublicAsset(assetKey: String): Either<CoreFailure, ByteArray>
    suspend fun downloadUsersPictureAssets(assetId: List<UserAssetId?>): Either<CoreFailure, Unit>
}

internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetMapper: AssetMapper,
    private val assetDao: AssetDAO
) : AssetRepository {

    override suspend fun uploadAndPersistPublicAsset(uploadAssetData: UploadAssetData): Either<NetworkFailure, UploadedAssetId> =
        suspending {
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
        if (persistedAsset != null) return@suspending Either.Right(persistedAsset.rawData)

        wrapApiRequest {
            assetApi.downloadAsset(assetKey, null)
        }.map { assetData ->
            assetDao.insertAsset(assetMapper.fromUserAssetToDaoModel(assetKey, assetData))
            assetData
        }
    }

    override suspend fun downloadUsersPictureAssets(assetId: List<UserAssetId?>): Either<CoreFailure, Unit> = suspending {
        assetId.filterNotNull().forEach {
            downloadPublicAsset(it)
        }
        return@suspending Either.Right(Unit)
    }
}
