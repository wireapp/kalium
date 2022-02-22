package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.db.AssetsQueries
import com.wire.kalium.persistence.db.Asset as SQLDelightAsset

class AssetMapper {
    fun toModel(asset: SQLDelightAsset): AssetEntity {
        return AssetEntity(
            asset.qualified_id,
            asset.token,
            asset.name,
            asset.encryption,
            asset.mime_type,
            asset.sha,
            asset.size,
            asset.downloaded
        )
    }
}

class AssetDAOImpl(private val queries: AssetsQueries) : AssetDAO {

    val mapper by lazy { AssetMapper() }

    override suspend fun insertAsset(assetEntity: AssetEntity) {
        queries.insertAsset(
            assetEntity.id,
            assetEntity.token,
            assetEntity.name,
            assetEntity.encryption,
            assetEntity.mimeType,
            assetEntity.sha,
            assetEntity.size,
            assetEntity.downloaded
        )
    }
}
