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
package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.datetime.Instant

data class AssetMessageEntity(
    val time: Instant,
    val username: String?,
    val messageId: String,
    val conversationId: QualifiedIDEntity,
    val assetId: String,
    val width: Int,
    val height: Int,
    val assetPath: String?,
    val isSelfAsset: Boolean
)

enum class AssetTransferStatusEntity {
    /**
     * The asset is currently being uploaded to remote storage.
     */
    UPLOAD_IN_PROGRESS,

    /**
     * The last attempt at uploading this asset's data failed.
     */
    FAILED_UPLOAD,

    /**
     * The asset was successfully uploaded and saved in the internal storage.
     */
    UPLOADED,

    /**
     * There was no attempt done to transfer the asset's data to/from remote (server) storage.
     */
    NOT_DOWNLOADED,

    /**
     * The asset is currently being downloaded from remote storage.
     */
    DOWNLOAD_IN_PROGRESS,

    /**
     * The last attempt at downloading this asset's data failed.
     */
    FAILED_DOWNLOAD,

    /**
     * The asset has been successfully downloaded and saved in the internal storage,
     * that should be only readable by this client. This state is used for downloaded assets saved internally.
     */
    SAVED_INTERNALLY,

    /**
     * The asset was downloaded and saved in an external storage, readable by other software on the machine.
     * e.g.: Asset was saved in Downloads, Desktop, or other user-chosen directory.
     */
    SAVED_EXTERNALLY,

    /**
     * Asset was not found on the server.
     */
    NOT_FOUND
}
