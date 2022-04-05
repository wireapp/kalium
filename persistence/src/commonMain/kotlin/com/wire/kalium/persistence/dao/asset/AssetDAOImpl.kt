package com.wire.kalium.persistence.dao.asset

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.db.AssetsQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Asset as SQLDelightAsset

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

    override suspend fun getAssetByKey(assetKey: String): Flow<AssetEntity?> {
        return queries.selectByKey(assetKey)
            .asFlow()
            .mapToOneOrNull()
            .map {
                it?.let {
                    return@map mapper.toModel(it)
                }
            }
    }

    override suspend fun updateAsset(assetEntity: AssetEntity) {
        queries.updateAsset(assetEntity.downloadedDate, assetEntity.rawData, assetEntity.mimeType, assetEntity.key)
    }
}
