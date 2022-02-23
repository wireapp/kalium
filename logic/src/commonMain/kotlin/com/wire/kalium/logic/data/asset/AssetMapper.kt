package com.wire.kalium.logic.data.asset

import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest
    fun toDomainModel(asset: AssetResponse): UploadedAssetId
    fun toDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity
}

class AssetMapperImpl : AssetMapper {
    override fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest {
        return AssetMetadataRequest(
            uploadAssetMetadata.mimeType.name,
            uploadAssetMetadata.isPublic,
            AssetRetentionType.valueOf(uploadAssetMetadata.retentionType.name),
            uploadAssetMetadata.md5
        )
    }

    override fun toDomainModel(asset: AssetResponse) =
        UploadedAssetId(asset.key)

    override fun toDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity {
        return AssetEntity(
            key = uploadedAssetResponse.key,
            domain = uploadedAssetResponse.domain,
            token = uploadedAssetResponse.token,
            name = java.util.UUID.randomUUID().toString(), // can be anything ?
            encryption = uploadAssetData.md5,
            mimeType = uploadAssetData.mimeType.name,
            sha = uploadAssetData.data,
            size = uploadAssetData.data.size.toLong(),
            downloaded = false
        )
    }
}
