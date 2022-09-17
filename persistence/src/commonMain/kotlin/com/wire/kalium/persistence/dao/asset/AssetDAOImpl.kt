package com.wire.kalium.persistence.dao.asset

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.AssetsQueries
import kotlinx.coroutines.flow.Flow

internal object AssetMapper {
    @Suppress("FunctionParameterNaming")
    fun fromAssets(
        key: String,
        domain: String,
        data_path: String,
        data_size: Long,
        downloaded_date: Long?
    ): AssetEntity {
        return AssetEntity(
            key = key,
            domain = domain,
            dataPath = data_path,
            dataSize = data_size,
            downloadedDate = downloaded_date
        )
    }
}

class AssetDAOImpl internal constructor(
    private val queries: AssetsQueries,
    private val mapper: AssetMapper = AssetMapper
    ) : AssetDAO {

    override suspend fun insertAsset(assetEntity: AssetEntity) {
        queries.insertAsset(
            assetEntity.key,
            assetEntity.domain,
            assetEntity.dataPath,
            assetEntity.dataSize,
            assetEntity.downloadedDate
        )
    }

    override suspend fun insertAssets(assetsEntity: List<AssetEntity>) {
        queries.transaction {
            assetsEntity.forEach { asset ->
                queries.insertAsset(
                    asset.key,
                    asset.domain,
                    asset.dataPath,
                    asset.dataSize,
                    asset.downloadedDate
                )
            }
        }
    }

    override suspend fun getAssetByKey(assetKey: String): Flow<AssetEntity?> {
        return queries.selectByKey(assetKey, mapper::fromAssets)
            .asFlow()
            .mapToOneOrNull()
    }

    override suspend fun updateAsset(assetEntity: AssetEntity) {
        queries.updateAsset(
            assetEntity.downloadedDate,
            assetEntity.dataPath,
            assetEntity.dataSize,
            assetEntity.key
        )
    }

    override suspend fun deleteAsset(key: String) {
        queries.deleteAsset(key)
    }
}
