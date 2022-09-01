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
    val assetType: String,
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

fun isValidImage(mimeType: String): Boolean = mimeType in setOf("image/jpg", "image/jpeg", "image/png", "image/heic")
