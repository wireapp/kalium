package com.wire.kalium.persistence.dao.asset

data class AssetEntity(
    val key: String,
    val domain: String,
    val mimeType: String?,
    val rawData: ByteArray?,
    val downloadedDate: Long?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AssetEntity

        if (key != other.key) return false
        if (domain != other.domain) return false
        if (mimeType != other.mimeType) return false
        if (!rawData.contentEquals(other.rawData)) return false
        if (downloadedDate != other.downloadedDate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + domain.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + rawData.contentHashCode()
        result = 31 * result + downloadedDate.hashCode()
        return result
    }
}

interface AssetDAO {
    suspend fun insertAsset(assetEntity: AssetEntity)
    suspend fun insertAssets(assetsEntity: List<AssetEntity>)
}
