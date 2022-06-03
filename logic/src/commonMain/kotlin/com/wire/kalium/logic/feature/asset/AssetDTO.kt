package com.wire.kalium.logic.feature.asset

data class AssetDTO(
    val assetName: String,
    val assetKey: String,
    val assetKeyDomain: String?,
    val assetToken: String?,
    val encryptionKey: ByteArray
)
