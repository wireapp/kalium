package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Clock

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest
    fun fromApiUploadResponseToDomainModel(asset: AssetResponse): UploadedAssetId
    fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity
    fun fromUserAssetIdToDaoModel(assetId: UserAssetId): AssetEntity
}

class AssetMapperImpl : AssetMapper {
    override fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest {
        return AssetMetadataRequest(
            uploadAssetMetadata.mimeType.name,
            uploadAssetMetadata.isPublic,
            AssetRetentionType.valueOf(uploadAssetMetadata.retentionType.name),
            calcMd5(uploadAssetMetadata.data)
        )
    }

    override fun fromApiUploadResponseToDomainModel(asset: AssetResponse) =
        UploadedAssetId(asset.key)

    override fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity {
        return AssetEntity(
            key = uploadedAssetResponse.key,
            domain = uploadedAssetResponse.domain,
            mimeType = uploadAssetData.mimeType.name,
            rawData = uploadAssetData.data, // should use something like byteArray to encrypt aes256cbc
            downloadedDate = Clock.System.now().toEpochMilliseconds()
        )
    }

    override fun fromUserAssetIdToDaoModel(assetId: UserAssetId): AssetEntity {
        return AssetEntity(assetId.toString(), "", null, "".toByteArray(), null)
    }
}
