package com.wire.kalium.logic.data.asset

import com.wire.kalium.network.api.model.Asset
import com.wire.kalium.network.api.model.AssetMetadata
import com.wire.kalium.network.api.model.AssetRetentionType

fun UploadAssetMetadata.toApiModel(md5: String): AssetMetadata {
    return AssetMetadata(mimeType, isPublic, AssetRetentionType.valueOf(retentionType.name), md5)
}

fun Asset.toDomainModel(): UploadAssetId = UploadAssetId(key)
