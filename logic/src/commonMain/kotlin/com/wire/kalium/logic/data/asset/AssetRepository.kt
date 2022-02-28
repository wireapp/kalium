package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
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

    override suspend fun uploadPublicAsset(uploadAssetData: UploadAssetData): Either<NetworkFailure, UploadedAssetId> =
        wrapApiRequest {
            assetMapper.toMetadataApiModel(uploadAssetData).let { metaData ->
                assetApi.uploadAsset(metaData, uploadAssetData.data)
            }
        }.map { assetMapper.toDomainModel(it) }

//    val uploadedAsset = assetApi.uploadAsset(assetMapper.toMetadataApiModel(uploadAssetData), uploadAssetData.data)
//    return if (!uploadedAsset.isSuccessful()) {
//        Either.Left(CoreFailure.ServerMiscommunication)
//    } else {
//        val assetEntity = assetMapper.toDaoModel(uploadAssetData, uploadedAsset.value)
//        assetDao.insertAsset(assetEntity)
//
//        val uploadedAssetId = assetMapper.toDomainModel(uploadedAsset.value)
//        Either.Right(uploadedAssetId)
//    }

    override suspend fun saveUserPictureAsset(assetId: List<UserAssetId>): Either<CoreFailure, Unit> {
        assetDao.insertAssets(assetId.map { assetMapper.fromUserAssetIdToDaoModel(it) })
        return Either.Right(Unit)
    }

}
