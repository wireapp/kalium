package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.dao.QualifiedID

data class AssetEntity(
    val id: QualifiedID,
    val token: String?,
    val name: String?,
    val encryption: String?,
    val mimeType: String?,
    val sha: ByteArray,
    val size: Long,
    val downloaded: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AssetEntity

        if (id != other.id) return false
        if (token != other.token) return false
        if (name != other.name) return false
        if (encryption != other.encryption) return false
        if (mimeType != other.mimeType) return false
        if (!sha.contentEquals(other.sha)) return false
        if (size != other.size) return false
        if (downloaded != other.downloaded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + encryption.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sha.contentHashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + downloaded.hashCode()
        return result
    }
}

interface AssetDAO {
    suspend fun insertAsset(assetEntity: AssetEntity)
}
