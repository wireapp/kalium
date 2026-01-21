/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.asset.isAudioMimeType
import com.wire.kalium.logic.data.asset.isDisplayableImageMimeType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.util.isGreaterThan
import okio.Path

internal data class UploadAssetMessageMetadata(
    val conversationId: ConversationId,
    val mimeType: String,
    val assetId: UploadedAssetId,
    val assetDataPath: Path,
    val assetDataSize: Long,
    val assetName: String,
    val assetWidth: Int?,
    val assetHeight: Int?,
    val otrKey: AES256Key,
    val sha256Key: SHA256Key,
    val audioLengthInMs: Long,
    val audioNormalizedLoudness: ByteArray?
) {
    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as UploadAssetMessageMetadata
        if (assetDataSize != other.assetDataSize) return false
        if (assetWidth != other.assetWidth) return false
        if (assetHeight != other.assetHeight) return false
        if (audioLengthInMs != other.audioLengthInMs) return false
        if (conversationId != other.conversationId) return false
        if (mimeType != other.mimeType) return false
        if (assetId != other.assetId) return false
        if (assetDataPath != other.assetDataPath) return false
        if (assetName != other.assetName) return false
        if (otrKey != other.otrKey) return false
        if (sha256Key != other.sha256Key) return false
        if (!audioNormalizedLoudness.contentEquals(other.audioNormalizedLoudness)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = assetDataSize.hashCode()
        result = 31 * result + (assetWidth ?: 0)
        result = 31 * result + (assetHeight ?: 0)
        result = 31 * result + audioLengthInMs.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + assetId.hashCode()
        result = 31 * result + assetDataPath.hashCode()
        result = 31 * result + assetName.hashCode()
        result = 31 * result + otrKey.hashCode()
        result = 31 * result + sha256Key.hashCode()
        result = 31 * result + (audioNormalizedLoudness?.contentHashCode() ?: 0)
        return result
    }
}

internal fun AssetUploadParams.createTempAssetMetadata(assetKey: String, path: Path) = UploadAssetMessageMetadata(
    conversationId = conversationId,
    mimeType = assetMimeType,
    assetDataPath = path,
    assetDataSize = assetDataSize,
    assetName = assetName,
    assetWidth = assetWidth,
    assetHeight = assetHeight,
    otrKey = generateRandomAES256Key(),
    // Sha256 will be replaced with right values after asset upload
    sha256Key = SHA256Key(byteArrayOf()),
    // Asset ID will be replaced with right value after asset upload
    assetId = UploadedAssetId(assetKey, ""),
    audioLengthInMs = audioLengthInMs,
    audioNormalizedLoudness = audioNormalizedLoudness
)

internal fun UploadAssetMessageMetadata.toAssetContent() = AssetContent(
    sizeInBytes = assetDataSize,
    name = assetName,
    mimeType = mimeType,
    metadata = when {
        isDisplayableImageMimeType(mimeType) && (assetHeight.isGreaterThan(0) && (assetWidth.isGreaterThan(0))) -> {
            AssetContent.AssetMetadata.Image(assetWidth, assetHeight)
        }

        isAudioMimeType(mimeType) -> {
            AssetContent.AssetMetadata.Audio(
                durationMs = audioLengthInMs,
                normalizedLoudness = audioNormalizedLoudness
            )
        }

        else -> null
    },
    remoteData = AssetContent.RemoteData(
        otrKey = otrKey.data,
        sha256 = sha256Key.data,
        assetId = assetId.key,
        encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
        assetDomain = assetId.domain,
        assetToken = assetId.assetToken
    ),
)
