package com.wire.kalium.logic.data.asset

import okio.Path

data class UploadedAssetId(
    val key: String,
    val domain: String? = null,
    val assetToken: String? = null
)

/**
 * On creation of this model, the use case should "calculate" the logic.
 * For example, rules that apply for an eternal/public asset
 */
data class UploadAssetData(
    val tempEncryptedDataPath: Path,
    val dataSize: Long,
    val assetType: AssetType,
    val isPublic: Boolean,
    val retentionType: RetentionType
)

enum class RetentionType {
    ETERNAL,
    PERSISTENT,
    VOLATILE,
    ETERNAL_INFREQUENT_ACCESS,
    EXPIRING
}

sealed class AssetType(open val mimeType: String)
data class FileAsset(val fileExtension: String) : AssetType("file/$fileExtension")
sealed class ImageAsset(override val mimeType: String) : AssetType(mimeType) {
    object JPEG : ImageAsset(mimeType = "image/jpeg")
    object JPG : ImageAsset(mimeType = "image/jpg")
    object PNG : ImageAsset(mimeType = "image/png")
}

fun isValidImage(mimeType: String): Boolean =
    when (mimeType) {
        "image/jpg" -> true
        "image/jpeg" -> true
        "image/png" -> true
        else -> false
    }

// should put other types of mimetypes, ie: media, audio, etc.
