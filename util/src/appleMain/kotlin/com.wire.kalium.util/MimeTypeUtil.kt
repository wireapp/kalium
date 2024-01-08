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
package com.wire.kalium.util

actual fun getExtensionFromMimeType(mimeType: String?): String? =
    mimeType?.let { mimeTypeToExtensionMap[it.lowercase()] } // TODO: replace with a proper platform implementation

private val mimeTypeToExtensionMap: Map<String, String> = // TODO: remove after all platforms have their own proper implementations
    mapOf(
        "image/jpg" to "jpg",
        "image/jpeg" to "jpeg",
        "image/png" to "png",
        "image/heic" to "heic",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "audio/mpeg" to "mp3",
        "audio/ogg" to "ogg",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/x-pn-wav" to "wav",
        "video/mp4" to "mp4",
        "video/webm" to "webm",
        "video/3gpp" to "3gpp",
        "video/mkv" to "mkv",
        "application/zip" to "zip",
        "application/pdf" to "pdf"
    )
