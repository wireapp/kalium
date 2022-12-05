package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key

@Suppress("LongParameterList")
internal class DownloadAssetMessageMetadata(
    val assetName: String,
    val assetSize: Long,
    val assetKey: String,
    val assetKeyDomain: String?,
    val assetToken: String?,
    val encryptionKey: AES256Key,
    val assetSHA256Key: SHA256Key,
)
