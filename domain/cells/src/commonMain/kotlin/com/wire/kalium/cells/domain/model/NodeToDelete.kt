/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.model

/**
 * A node picked for a bulk delete.
 * @param uuid identifies the node, both on the server and locally. Unlike a path, it still holds if the node was renamed
 * or moved meanwhile.
 * @param localPath the local copy of a file to remove along with it, null for folders and files never downloaded.
 * @param folderPath for a folder, its remote path, to also clean up the local copies of the files stored under it: they
 * are only known locally by the path they were saved with. Null for a file. Not used for the server request.
 */
public data class NodeToDelete(
    val uuid: String,
    val localPath: String?,
    val folderPath: String? = null,
)
