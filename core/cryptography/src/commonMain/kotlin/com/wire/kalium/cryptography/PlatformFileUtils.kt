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
package com.wire.kalium.cryptography

/**
 * Creates a directory at the specified path, including any necessary parent directories.
 * @param path The absolute path of the directory to create
 * @return true if the directory was created or already exists, false otherwise
 */
internal expect fun createDirectory(path: String): Boolean

/**
 * Checks if a file or directory exists at the specified path.
 * @param path The absolute path to check
 * @return true if the file or directory exists, false otherwise
 */
internal expect fun fileExists(path: String): Boolean

/**
 * Deletes a file or directory at the specified path.
 * @param path The absolute path to delete
 * @return true if the file was deleted successfully, false otherwise
 */
internal expect fun deleteFile(path: String): Boolean
