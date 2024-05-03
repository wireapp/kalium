/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.sync.receiver.conversation.message.hasValidRemoteData

data class AssetContent(
    val sizeInBytes: Long,
    val name: String? = null,
    val mimeType: String,
    val metadata: AssetMetadata? = null,
    val remoteData: RemoteData
) {

    private val isPreviewMessage = sizeInBytes > 0 && !hasValidRemoteData()

    private val hasValidImageMetadata = when (metadata) {
        is AssetMetadata.Image -> metadata.width > 0 && metadata.height > 0
        else -> false
    }

    // We should not display Preview Assets (assets w/o valid encryption keys sent by Mac/Web clients) unless they include image metadata
    val shouldBeDisplayed = !isPreviewMessage || hasValidImageMetadata

    sealed class AssetMetadata {
        data class Image(val width: Int, val height: Int) : AssetMetadata()
        data class Video(val width: Int?, val height: Int?, val durationMs: Long?) : AssetMetadata()
        data class Audio(val durationMs: Long?, val normalizedLoudness: ByteArray?) : AssetMetadata() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as Audio

                if (durationMs != other.durationMs) return false
                if (normalizedLoudness != null) {
                    if (other.normalizedLoudness == null) return false
                    if (!normalizedLoudness.contentEquals(other.normalizedLoudness)) return false
                } else if (other.normalizedLoudness != null) return false

                return true
            }

            override fun hashCode(): Int {
                var result = durationMs?.hashCode() ?: 0
                result = 31 * result + (normalizedLoudness?.contentHashCode() ?: 0)
                return result
            }
        }
    }

    data class RemoteData(
        val otrKey: ByteArray,
        val sha256: ByteArray,
        val assetId: String,
        val assetToken: String?,
        val assetDomain: String?,
        val encryptionAlgorithm: MessageEncryptionAlgorithm?
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as RemoteData

            if (!otrKey.contentEquals(other.otrKey)) return false
            if (!sha256.contentEquals(other.sha256)) return false
            if (assetId != other.assetId) return false
            if (assetToken != other.assetToken) return false
            if (assetDomain != other.assetDomain) return false
            if (encryptionAlgorithm != other.encryptionAlgorithm) return false

            return true
        }

        override fun hashCode(): Int {
            var result = otrKey.contentHashCode()
            result = 31 * result + sha256.contentHashCode()
            result = 31 * result + assetId.hashCode()
            result = 31 * result + (assetToken?.hashCode() ?: 0)
            result = 31 * result + (assetDomain?.hashCode() ?: 0)
            result = 31 * result + (encryptionAlgorithm?.hashCode() ?: 0)
            return result
        }
    }
}
