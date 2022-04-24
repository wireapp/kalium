package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.AssetsQueries
import com.wire.kalium.persistence.Asset as SQLDelightAsset

class AssetMapper {
    fun toModel(asset: SQLDelightAsset): AssetEntity {
        return AssetEntity(
            key = asset.key,
            domain = asset.domain,
            mimeType = asset.mime_type,
            rawData = asset.raw_data,
            downloadedDate = asset.downloaded_date
        )
    }
}

class AssetDAOImpl(private val queries: AssetsQueries) : AssetDAO {

    val mapper by lazy { AssetMapper() }

    override fun insertAsset(assetEntity: AssetEntity) {
        queries.insertAsset(
            assetEntity.key,
            assetEntity.domain,
            assetEntity.mimeType,
            assetEntity.rawData,
            assetEntity.downloadedDate
        )
    }

    override fun insertAssets(assetsEntity: List<AssetEntity>) {
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

    override fun getAssetByKey(assetKey: String): AssetEntity? =
        queries.selectByKey(assetKey).executeAsOneOrNull()?.let { mapper.toModel(it) }

    override fun updateAsset(assetEntity: AssetEntity) {
        queries.updateAsset(assetEntity.downloadedDate, assetEntity.rawData, assetEntity.mimeType, assetEntity.key)
    }
}
