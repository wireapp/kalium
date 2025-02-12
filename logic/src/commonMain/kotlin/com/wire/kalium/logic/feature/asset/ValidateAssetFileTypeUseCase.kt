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
package com.wire.kalium.logic.feature.asset

import com.wire.kalium.common.logger.kaliumLogger

/**
 * Returns true if the file extension is present in file name and is allowed and false otherwise.
 * @param fileName the file name (with extension) to validate.
 * @param allowedExtension the list of allowed extension.
 */
interface ValidateAssetFileTypeUseCase {
    operator fun invoke(
        fileName: String?,
        mimeType: String,
        allowedExtension: List<String>
    ): Boolean
}

internal class ValidateAssetFileTypeUseCaseImpl : ValidateAssetFileTypeUseCase {
    override operator fun invoke(
        fileName: String?,
        mimeType: String,
        allowedExtension: List<String>
    ): Boolean {
        kaliumLogger.d("Validating file type for $fileName with mimeType $mimeType is empty ${mimeType.isBlank()}")
        val extension = if (fileName != null) {
            extensionFromFileName(fileName)
        } else {
            extensionFromMimeType(mimeType)
        }
        return extension?.let { allowedExtension.contains(it) } ?: false
    }

    private fun extensionFromFileName(fileName: String): String? =
        fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }

    private fun extensionFromMimeType(mimeType: String): String? = fileExtensions[mimeType]

    private companion object {
        val fileExtensions = mapOf(
            "video/3gpp" to "3gpp",
            "audio/aac" to "aac",
            "audio/amr" to "amr",
            "video/x-msvideo" to "avi",
            "image/bmp" to "bmp",
            "text/css" to "css",
            "text/csv" to "csv",
            "application/msword" to "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
            "message/rfc822" to "eml",
            "audio/flac" to "flac",
            "image/gif" to "gif",
            "text/html" to "html",
            "image/vnd.microsoft.icon" to "ico",
            "image/jpeg" to "jpeg",
            "image/jpeg" to "jpg",
            "image/jpeg" to "jfif",
            "application/vnd.apple.keynote" to "key",
            "audio/mp4" to "m4a",
            "video/x-m4v" to "m4v",
            "text/markdown" to "md",
            "audio/midi" to "midi",
            "video/x-matroska" to "mkv",
            "video/quicktime" to "mov",
            "audio/mpeg" to "mp3",
            "video/mp4" to "mp4",
            "video/mpeg" to "mpeg",
            "application/vnd.ms-outlook" to "msg",
            "application/vnd.oasis.opendocument.spreadsheet" to "ods",
            "application/vnd.oasis.opendocument.text" to "odt",
            "audio/ogg" to "ogg",
            "application/pdf" to "pdf",
            "image/jpeg" to "pjp",
            "image/pjpeg" to "pjpeg",
            "image/png" to "png",
            "application/vnd.ms-powerpoint" to "ppt",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
            "image/vnd.adobe.photoshop" to "psd",
            "application/rtf" to "rtf",
            "application/sql" to "sql",
            "image/svg+xml" to "svg",
            "application/x-tex" to "tex",
            "image/tiff" to "tiff",
            "text/plain" to "txt",
            "text/x-vcard" to "vcf",
            "audio/wav" to "wav",
            "video/webm" to "webm",
            "image/webp" to "webp",
            "video/x-ms-wmv" to "wmv",
            "application/vnd.ms-excel" to "xls",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
            "application/xml" to "xml"
        )
    }
}
