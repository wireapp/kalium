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
package com.wire.kalium.cells.domain

import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import kotlinx.coroutines.flow.Flow
import okio.Path

public interface CellUploadManager {
    /**
     * Starts file upload to the cell. Returns immediately after pre-checking the file name and returns the new node.
     * The upload is done in the background and can be observed with [observeUpload] function.
     * @param assetPath path to the file to upload
     * @param assetSize size of the file to upload
     * @param destNodePath path to the node where the file should be uploaded (cellName/fileName)
     * @return [Either] with the new [CellNode] or [NetworkFailure]
     */
    public suspend fun upload(assetPath: Path, assetSize: Long, destNodePath: String): Either<NetworkFailure, CellNode>

    /**
     * Observe upload events for the node with [nodeUuid].
     * @param nodeUuid UUID of the node to observe
     * @return [Flow] of [CellUploadEvent] or null if the node is not being uploaded
     */
    public fun observeUpload(nodeUuid: String): Flow<CellUploadEvent>?

    /**
     * Cancel upload of the node with [nodeUuid].
     * @param nodeUuid UUID of the node to cancel
     */
    public suspend fun cancelUpload(nodeUuid: String)

    /**
     * Get upload info for the node with [nodeUuid].
     * @param nodeUuid UUID of the node to get info for
     * @return [CellUploadInfo] or null if the node is not being uploaded
     */
    public fun getUploadInfo(nodeUuid: String): CellUploadInfo?

    /**
     * Check if the node with [nodeUuid] is being uploaded.
     * @param nodeUuid UUID of the node to check
     * @return true if the node is being uploaded
     */
    public fun isUploading(nodeUuid: String): Boolean
}

/**
 * Information about the upload of the file.
 * @param progress upload progress
 * @param uploadFailed true if the upload failed
 */
public data class CellUploadInfo(
    val progress: Float = 0f,
    val uploadFailed: Boolean = false,
)

public sealed interface CellUploadEvent {
    public data class UploadProgress(val progress: Float) : CellUploadEvent
    public data object UploadCompleted : CellUploadEvent
    public data object UploadError : CellUploadEvent
    public data object UploadCancelled : CellUploadEvent
}
