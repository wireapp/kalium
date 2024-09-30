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

package com.wire.kalium.logic.data.asset

import okio.Path

data class UploadedAssetId(
    val key: String,
    val domain: String,
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

enum class AttachmentType {
    IMAGE, GENERIC_FILE, AUDIO, VIDEO;

    companion object {
        fun fromMimeTypeString(mimeType: String): AttachmentType = when {
            isDisplayableImageMimeType(mimeType) -> IMAGE
            isAudioMimeType(mimeType) -> AUDIO
            isVideoMimeType(mimeType) -> VIDEO
            else -> GENERIC_FILE
        }
    }
}

fun isDisplayableImageMimeType(mimeType: String): Boolean = mimeType in SUPPORTED_IMAGE_ASSET_MIME_TYPES

fun isAudioMimeType(mimeType: String): Boolean = mimeType in SUPPORTED_AUDIO_ASSET_MIME_TYPES
fun isVideoMimeType(mimeType: String): Boolean = mimeType in SUPPORTED_VIDEO_ASSET_MIME_TYPES

val SUPPORTED_IMAGE_ASSET_MIME_TYPES = setOf("image/jpg", "image/jpeg", "image/png", "image/gif", "image/webp")
val SUPPORTED_AUDIO_ASSET_MIME_TYPES = setOf(
    "audio/mp3", "audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav", "audio/x-wav", "audio/x-pn-wav", "audio/x-m4a"
)
val SUPPORTED_VIDEO_ASSET_MIME_TYPES = setOf("video/mp4", "video/webm", "video/3gpp", "video/mkv")
