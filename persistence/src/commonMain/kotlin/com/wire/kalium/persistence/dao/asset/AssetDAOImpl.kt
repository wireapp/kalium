package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.db.AssetsQueries
import com.wire.kalium.persistence.db.Asset as SQLDelightAsset

class AssetMapper {
    fun toModel(asset: SQLDelightAsset): AssetEntity {
        return AssetEntity(
            asset.key,
            asset.domain,
            asset.token,
            asset.name,
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
            assetEntity.key,
            assetEntity.domain,
            assetEntity.token,
            assetEntity.name,
            assetEntity.mimeType,
            assetEntity.sha,
            assetEntity.size,
            assetEntity.downloaded
        )
    }

    override suspend fun insertAssets(assetsEntity: List<AssetEntity>) {
        queries.transaction {
            assetsEntity.forEach { asset ->
                queries.insertAsset(
                    asset.key,
                    asset.domain,
                    asset.token,
                    asset.name,
                    asset.mimeType,
                    asset.sha,
                    asset.size,
                    asset.downloaded
                )
            }
        }
    }
}
