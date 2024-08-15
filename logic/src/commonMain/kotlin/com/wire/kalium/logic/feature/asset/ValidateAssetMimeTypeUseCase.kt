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

/**
 * Returns true if the file extension is present in file name and is allowed and false otherwise.
 * @param fileName the file name (with extension) to validate.
 * @param allowedExtension the list of allowed extension.
 */
interface ValidateAssetMimeTypeUseCase {
    operator fun invoke(fileName: String, allowedExtension: List<String>): Boolean
}

internal class ValidateAssetMimeTypeUseCaseImpl : ValidateAssetMimeTypeUseCase {
    override operator fun invoke(fileName: String, allowedExtension: List<String>): Boolean {
        val split = fileName.split(".")
        return if (split.size < 2) {
            false
        } else {
            val extension = split.last().lowercase()
            allowedExtension.any { it.lowercase() == extension }
        }
    }
}
