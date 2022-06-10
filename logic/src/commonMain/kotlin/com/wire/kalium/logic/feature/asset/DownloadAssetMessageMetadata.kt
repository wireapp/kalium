package com.wire.kalium.logic.feature.asset

internal class DownloadAssetMessageMetadata(
    val assetKey: String,
    val assetDomain: String?,
    val assetToken: String?,
    val assetEncryptionKey: ByteArray
)
