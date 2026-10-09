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

    /**
     * Whether any upload is still queued or running.
     *
     * Kept here rather than derived by each caller so the platform layer keeping the process alive in the
     * background reads the same definition of "still working" the scheduler itself uses.
     */
    public val hasActiveUploads: StateFlow<Boolean>

    /** Adds [requests] to the end of the queue. They start as soon as the concurrency rules allow. */
    public fun enqueue(requests: List<CellUploadRequest>)

    /** Cancels a queued or running upload, aborting the underlying transfer. */
    public fun cancel(id: String)

    /** Cancels every queued and running upload, across every conversation. */
    public fun cancelAll()

    /** Cancels every queued and running upload whose [CellUploadItem.conversationId] is [conversationId]. */
    public fun cancelAll(conversationId: String)

    /** Re-queues a failed or cancelled upload. Completed and running uploads are untouched. */
    public fun retry(id: String)

    /** Re-queues every failed upload, in their original order, across every conversation. */
    public fun retryAllFailed()

    /** Re-queues every failed upload whose [CellUploadItem.conversationId] is [conversationId], in original order. */
    public fun retryAllFailed(conversationId: String)

    /** Removes a finished (completed, failed or cancelled) upload from [uploads]. Active uploads are untouched. */
    public fun dismiss(id: String)

    /** Removes every finished upload from [uploads], across every conversation. Active uploads are untouched. */
    public fun dismissAll()

    /**
     * Removes every finished upload whose [CellUploadItem.conversationId] is [conversationId] from [uploads].
     * Active uploads are untouched.
     */
    public fun dismissAll(conversationId: String)

    /**
     * Registers [fileName] as about to be uploaded into [conversationId], before its local content is staged
     * and ready, so it is visible in [uploads] (as [CellUploadState.Preparing]) immediately rather than only
     * once staging finishes. Returns an id to pass to [attachPreparedRequest] once staging succeeds, or
     * [failPreparing] if it doesn't.
     */
    public fun beginPreparing(fileName: String, conversationId: String): String

    /** Attaches the now-staged [request] to the item [id] returned by [beginPreparing], moving it to Queued. */
    public fun attachPreparedRequest(id: String, request: CellUploadRequest)

    /** Removes the preparing item [id] from [uploads] after it failed to stage. */
    public fun failPreparing(id: String)
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
    public data class Preparing(val fileName: String) : CellUploadState
    public data object Queued : CellUploadState
    public data class Uploading(val progress: Float = 0f) : CellUploadState
    public data object Completed : CellUploadState
    public data object Failed : CellUploadState
    public data object Cancelled : CellUploadState
}

/**
 * A single upload tracked by [CellUploadCoordinator]: the requested file plus its current lifecycle state.
 *
 * [request] is null only while [state] is [CellUploadState.Preparing], since the local file it points to
 * does not exist yet at that point; every other state is reached exclusively through
 * [CellUploadCoordinator.attachPreparedRequest], which always sets both together.
 */
public data class CellUploadItem(
    val id: String,
    /**
     * Root segment of the destination path, identifying which conversation's Shared Drive this upload
     * belongs to. Known from the moment the item is created (even while [state] is still [CellUploadState.Preparing]
     * and [request] is null yet), so callers can group or filter uploads by conversation without waiting
     * for staging to finish.
     */
    val conversationId: String,
    val request: CellUploadRequest? = null,
    val state: CellUploadState = CellUploadState.Queued,
    /** Draft node created by [CellUploadManager] once the transfer starts, needed to cancel or retry it. */
    val nodeUuid: String? = null,
    /** Draft version created alongside [nodeUuid], needed to publish the node once the transfer succeeds. */
    val versionId: String? = null,
) {
    public val fileName: String get() = request?.fileName ?: (state as? CellUploadState.Preparing)?.fileName.orEmpty()
    public val sizeBytes: Long get() = request?.sizeBytes ?: 0L
}

/**
 * Whether [CellUploadState] represents work the coordinator still has to do for that item, as opposed to a
 * terminal outcome (completed, failed, cancelled). Centralized so background-liveness, status icons and
 * notification state all agree on what counts as "still going" without redefining the same list four times.
 */
public val CellUploadState.isActive: Boolean
    get() = this is CellUploadState.Preparing || this is CellUploadState.Queued || this is CellUploadState.Uploading
