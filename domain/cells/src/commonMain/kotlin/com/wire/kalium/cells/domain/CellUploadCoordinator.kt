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
package com.wire.kalium.cells.domain

import kotlinx.coroutines.flow.StateFlow
import okio.Path

/**
 * Schedules a batch of file uploads into cell folders.
 *
 * [CellUploadManager] starts every upload it is given immediately, with no ordering or limit. This
 * coordinator adds what it lacks: an ordered queue, admission control, aggregate per-file state,
 * cancellation and retry. The transfer itself stays with the manager.
 */
public interface CellUploadCoordinator {

    /** Every upload of the current session, in the order the files were picked. */
    public val uploads: StateFlow<List<CellUploadItem>>

    /** Adds [requests] to the end of the queue. They start as soon as the concurrency rules allow. */
    public fun enqueue(requests: List<CellUploadRequest>)

    /** Cancels a queued or running upload, aborting the underlying transfer. */
    public fun cancel(id: String)

    /** Cancels every queued and running upload. */
    public fun cancelAll()

    /** Re-queues a failed upload. Completed and running uploads are untouched. */
    public fun retry(id: String)

    /** Re-queues every failed upload, in their original order. */
    public fun retryAllFailed()
}

/**
 * A file to upload into a cell folder.
 *
 * @param localPath local copy of the file, which has to stay readable until the upload ends
 * @param destinationFolderPath folder receiving the file, as `cellName[/subFolder...]`
 */
public data class CellUploadRequest(
    val localPath: Path,
    val fileName: String,
    val sizeBytes: Long,
    val destinationFolderPath: String,
)

/**
 * Lifecycle of a single upload.
 */
public sealed interface CellUploadState {
    public data object Queued : CellUploadState
    public data class Uploading(val progress: Float = 0f) : CellUploadState
    public data object Completed : CellUploadState
    public data object Failed : CellUploadState
    public data object Cancelled : CellUploadState
}

/**
 * A single upload tracked by [CellUploadCoordinator]: the requested file plus its current lifecycle state.
 */
public data class CellUploadItem(
    val id: String,
    val request: CellUploadRequest,
    val state: CellUploadState = CellUploadState.Queued,
    /** Draft node created by [CellUploadManager] once the transfer starts, needed to cancel or retry it. */
    val nodeUuid: String? = null,
) {
    public val fileName: String get() = request.fileName
    public val sizeBytes: Long get() = request.sizeBytes
}