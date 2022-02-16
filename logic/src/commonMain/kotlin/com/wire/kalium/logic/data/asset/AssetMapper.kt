package com.wire.kalium.logic.data.asset

import com.wire.kalium.network.api.model.AssetMetadataRequest
import com.wire.kalium.network.api.model.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest
    fun toDomainModel(asset: AssetResponse): UploadedAssetId
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

    override fun toDomainModel(asset: AssetResponse) = UploadedAssetId(asset.key)
}
