package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import kotlinx.coroutines.flow.firstOrNull

interface AssetRepository {
    suspend fun uploadAndPersistPublicAsset(mimeType: AssetType, assetData: ByteArray): Either<CoreFailure, UploadedAssetId>
    suspend fun uploadAndPersistPrivateAsset(mimeType: AssetType, assetData: ByteArray): Either<CoreFailure, UploadedAssetId>
    suspend fun downloadPublicAsset(assetKey: String): Either<CoreFailure, ByteArray>
    suspend fun downloadUsersPictureAssets(assetId: List<UserAssetId?>): Either<CoreFailure, Unit>
}

internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetDao: AssetDAO,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper()
) : AssetRepository {

    override suspend fun uploadAndPersistPublicAsset(mimeType: AssetType, assetData: ByteArray): Either<CoreFailure, UploadedAssetId> {
        val uploadAssetData = UploadAssetData(assetData, mimeType, true, RetentionType.ETERNAL)
        return uploadAndPersistAsset(uploadAssetData)
    }

    override suspend fun uploadAndPersistPrivateAsset(mimeType: AssetType, assetData: ByteArray): Either<CoreFailure, UploadedAssetId> {
        val uploadAssetData = UploadAssetData(assetData, mimeType, false, RetentionType.PERSISTENT)
        return uploadAndPersistAsset(uploadAssetData)
    }

    private suspend fun uploadAndPersistAsset(uploadAssetData: UploadAssetData): Either<CoreFailure, UploadedAssetId> = suspending {
        assetMapper.toMetadataApiModel(uploadAssetData).let { metaData ->
            wrapApiRequest {
                // we should also consider for avatar images, the compression for preview vs complete picture
                assetApi.uploadAsset(metaData, uploadAssetData.data)
            }
        }.flatMap { assetResponse ->
            assetMapper.fromUploadedAssetToDaoModel(uploadAssetData, assetResponse).let { assetEntity ->
                wrapStorageRequest { assetDao.insertAsset(assetEntity) }
            }.map { assetMapper.fromApiUploadResponseToDomainModel(assetResponse) }
        }
    }

    override suspend fun downloadPublicAsset(assetKey: String): Either<CoreFailure, ByteArray> = suspending {
        // TODO: handle storage error
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
