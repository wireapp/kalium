package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.db.AssetsQueries
import com.wire.kalium.persistence.db.Asset as SQLDelightAsset

class AssetMapper {
    fun toModel(asset: SQLDelightAsset): AssetEntity {
        return AssetEntity(
            asset.key,
            asset.domain,
            asset.mime_type,
            asset.raw_data,
            asset.downloaded_date
        )
    }
}

class AssetDAOImpl(private val queries: AssetsQueries) : AssetDAO {

    val mapper by lazy { AssetMapper() }

    override suspend fun insertAsset(assetEntity: AssetEntity) {
        queries.insertAsset(
            assetEntity.key,
            assetEntity.domain,
            assetEntity.mimeType,
            assetEntity.rawData,
            assetEntity.downloadedDate
        )
    }

    override suspend fun insertAssets(assetsEntity: List<AssetEntity>) {
        queries.transaction {
            assetsEntity.forEach { asset ->
                queries.insertAsset(
                    asset.key,
                    asset.domain,
                    asset.mimeType,
                    asset.rawData,
                    asset.downloadedDate
                )
            }
        }
    }
}
