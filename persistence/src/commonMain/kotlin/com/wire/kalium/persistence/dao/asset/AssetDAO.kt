package com.wire.kalium.persistence.dao.asset

import kotlinx.coroutines.flow.Flow

data class AssetEntity(
    val key: String,
    val domain: String,
    val mimeType: String?,
    val dataPath: String,
    val dataSize: Long,
    val assetToken: String? = null,
    val downloadedDate: Long?
)

interface AssetDAO {
    suspend fun insertAsset(assetEntity: AssetEntity)
    suspend fun insertAssets(assetsEntity: List<AssetEntity>)
    suspend fun getAssetByKey(assetKey: String): Flow<AssetEntity?>
    suspend fun updateAsset(assetEntity: AssetEntity)
}
