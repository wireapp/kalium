package com.wire.kalium.logic.data.asset

data class UploadedAssetId(val key: String, val assetToken: String? = null)

/**
 * On creation of this model, the use case should "calculate" the logic.
 * For example, rules that apply for an eternal/public asset
 */
data class UploadAssetData(
    val data: ByteArray,
    val mimeType: AssetType,
    val isPublic: Boolean,
    val retentionType: RetentionType
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UploadAssetData

        if (!data.contentEquals(other.data)) return false
        if (mimeType != other.mimeType) return false
        if (isPublic != other.isPublic) return false
        if (retentionType != other.retentionType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + isPublic.hashCode()
        result = 31 * result + retentionType.hashCode()
        return result
    }
}

enum class RetentionType {
    ETERNAL,
    PERSISTENT,
    VOLATILE,
    ETERNAL_INFREQUENT_ACCESS,
    EXPIRING
}

sealed class AssetType(open val name: String)

sealed class ImageAsset(override val name: String) : AssetType(name) {
    object JPEG : ImageAsset(name = "image/jpeg")
    object PNG : ImageAsset(name = "image/png")
}
// should put other types of mimetypes, ie: media, audio, etc.
