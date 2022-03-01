package com.wire.kalium.logic.data.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest
    fun toDomainModel(asset: AssetResponse): UploadedAssetId
    fun toDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity
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

    override fun toDomainModel(asset: AssetResponse) =
        UploadedAssetId(asset.key)

    override fun toDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity {
        return AssetEntity(
            key = uploadedAssetResponse.key,
            domain = uploadedAssetResponse.domain,
            token = uploadedAssetResponse.token,
            name = uuid4().toString(),
            encryption = null, // should use something like byteArray to encrypt aes256cbc
            mimeType = uploadAssetData.mimeType.name,
            sha = uploadAssetData.data, // should use something like byteArray to encrypt aes256cbc
            size = uploadAssetData.data.size.toLong(),
            downloaded = true
        )
    }

    override fun fromUserAssetIdToDaoModel(assetId: UserAssetId): AssetEntity {
        return AssetEntity(assetId.toString(), "", null, null, null, ImageAsset.JPG.name, null, 0, false)
    }
}
