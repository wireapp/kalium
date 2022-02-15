package com.wire.kalium.logic.data.asset

data class UploadAssetId(val key: String)

/**
 * On creation of this model, the use case should "calculate" the logic.
 * For example, rules that apply for an eternal/public asset
 */
data class UploadAssetMetadata(
    val mimeType: String,
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
