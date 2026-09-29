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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadInfo
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellUploadRequest
import com.wire.kalium.cells.domain.CellUploadState
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CellUploadCoordinatorTest {

    @Test
    fun given_upload_when_transfer_completes_then_item_is_published_and_completed() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()
        assertEquals(CellUploadState.Uploading(), arrangement.state("a.txt"))
        assertTrue(arrangement.coordinator.hasActiveUploads.value)

        arrangement.manager.emit("a.txt", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()

        assertEquals(CellUploadState.Completed, arrangement.state("a.txt"))
        assertEquals(1, arrangement.repository.publishDraftsCalls)
        assertFalse(arrangement.coordinator.hasActiveUploads.value)
        arrangement.close()
    }

    @Test
    fun given_progress_event_when_uploading_then_progress_is_reflected() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()

        arrangement.manager.emit("a.txt", CellUploadEvent.UploadProgress(0.5f))
        advanceUntilIdle()

        assertEquals(CellUploadState.Uploading(0.5f), arrangement.state("a.txt"))
        arrangement.close()
    }

    @Test
    fun given_upload_error_when_retry_then_existing_node_is_retried_and_completes() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()
        arrangement.manager.emit("a.txt", CellUploadEvent.UploadError)
        advanceUntilIdle()
        assertEquals(CellUploadState.Failed, arrangement.state("a.txt"))

        arrangement.coordinator.retry(arrangement.id("a.txt"))
        advanceUntilIdle()
        assertEquals(CellUploadState.Uploading(), arrangement.state("a.txt"))
        arrangement.manager.emit("a.txt", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()

        assertEquals(CellUploadState.Completed, arrangement.state("a.txt"))
        assertEquals(1, arrangement.manager.uploadCalls)
        assertEquals(1, arrangement.manager.retryUploadCalls)
        arrangement.close()
    }

    @Test
    fun given_failed_upload_when_manager_forgot_node_then_retry_creates_new_draft() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()
        arrangement.manager.emit("a.txt", CellUploadEvent.UploadError)
        advanceUntilIdle()
        arrangement.manager.forget("a.txt")

        arrangement.coordinator.retry(arrangement.id("a.txt"))
        advanceUntilIdle()

        assertEquals(2, arrangement.manager.uploadCalls)
        assertEquals(0, arrangement.manager.retryUploadCalls)
        assertEquals(CellUploadState.Uploading(), arrangement.state("a.txt"))
        arrangement.close()
    }

    @Test
    fun given_failed_uploads_when_retry_all_failed_then_all_are_requeued_and_restarted() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt"), request("b.txt")))
        advanceUntilIdle()
        arrangement.manager.emit("a.txt", CellUploadEvent.UploadError)
        arrangement.manager.emit("b.txt", CellUploadEvent.UploadError)
        advanceUntilIdle()
        assertEquals(CellUploadState.Failed, arrangement.state("a.txt"))
        assertEquals(CellUploadState.Failed, arrangement.state("b.txt"))

        arrangement.coordinator.retryAllFailed()
        advanceUntilIdle()

        assertEquals(CellUploadState.Uploading(), arrangement.state("a.txt"))
        assertEquals(CellUploadState.Uploading(), arrangement.state("b.txt"))
        assertEquals(2, arrangement.manager.retryUploadCalls)
        arrangement.close()
    }

    @Test
    fun given_more_uploads_than_slots_then_extra_uploads_wait_in_fifo_order() = runTest {
        val arrangement = Arrangement(this, maxConcurrentUploads = 2)
        arrangement.coordinator.enqueue(listOf(request("a.txt"), request("b.txt"), request("c.txt")))
        advanceUntilIdle()

        assertEquals(CellUploadState.Uploading(), arrangement.state("a.txt"))
        assertEquals(CellUploadState.Uploading(), arrangement.state("b.txt"))
        assertEquals(CellUploadState.Queued, arrangement.state("c.txt"))

        arrangement.manager.emit("b.txt", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()

        assertEquals(CellUploadState.Completed, arrangement.state("b.txt"))
        assertEquals(CellUploadState.Uploading(), arrangement.state("c.txt"))
        arrangement.close()
    }

    @Test
    fun given_large_file_at_head_then_it_waits_for_running_uploads_and_blocks_followers() = runTest {
        val arrangement = Arrangement(this, largeFileThresholdBytes = 100)
        arrangement.coordinator.enqueue(
            listOf(request("a.txt"), request("big.bin", size = 1000), request("c.txt"))
        )
        advanceUntilIdle()

        assertEquals(CellUploadState.Uploading(), arrangement.state("a.txt"))
        assertEquals(CellUploadState.Queued, arrangement.state("big.bin"))
        assertEquals(CellUploadState.Queued, arrangement.state("c.txt"))

        arrangement.manager.emit("a.txt", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()

        assertEquals(CellUploadState.Uploading(), arrangement.state("big.bin"))
        assertEquals(CellUploadState.Queued, arrangement.state("c.txt"))

        arrangement.manager.emit("big.bin", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()

        assertEquals(CellUploadState.Uploading(), arrangement.state("c.txt"))
        arrangement.close()
    }

    @Test
    fun given_uploading_item_when_cancel_then_manager_cancels_and_item_is_cancelled() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()

        arrangement.coordinator.cancel(arrangement.id("a.txt"))
        advanceUntilIdle()

        assertEquals(CellUploadState.Cancelled, arrangement.state("a.txt"))
        assertEquals(listOf("node-a.txt"), arrangement.manager.cancelled)
        assertFalse(arrangement.coordinator.hasActiveUploads.value)
        arrangement.close()
    }

    @Test
    fun given_queued_item_when_cancel_then_item_is_cancelled_without_calling_manager() = runTest {
        val arrangement = Arrangement(this, maxConcurrentUploads = 1)
        arrangement.coordinator.enqueue(listOf(request("a.txt"), request("b.txt")))
        advanceUntilIdle()

        arrangement.coordinator.cancel(arrangement.id("b.txt"))
        advanceUntilIdle()

        assertEquals(CellUploadState.Cancelled, arrangement.state("b.txt"))
        assertTrue(arrangement.manager.cancelled.isEmpty())
        arrangement.close()
    }

    @Test
    fun given_active_uploads_when_cancel_all_then_queued_and_uploading_items_are_cancelled() = runTest {
        val arrangement = Arrangement(this, maxConcurrentUploads = 1)
        arrangement.coordinator.enqueue(listOf(request("a.txt"), request("b.txt")))
        advanceUntilIdle()

        arrangement.coordinator.cancelAll()
        advanceUntilIdle()

        assertEquals(CellUploadState.Cancelled, arrangement.state("a.txt"))
        assertEquals(CellUploadState.Cancelled, arrangement.state("b.txt"))
        assertFalse(arrangement.coordinator.hasActiveUploads.value)
        arrangement.close()
    }

    @Test
    fun given_cancelled_item_when_retry_then_it_is_requeued_and_started() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()
        arrangement.coordinator.cancel(arrangement.id("a.txt"))
        advanceUntilIdle()

        arrangement.coordinator.retry(arrangement.id("a.txt"))
        advanceUntilIdle()

        assertEquals(CellUploadState.Uploading(), arrangement.state("a.txt"))
        arrangement.close()
    }

    @Test
    fun given_completed_item_when_cancel_or_retry_then_state_is_unchanged() = runTest {
        val arrangement = Arrangement(this)
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()
        arrangement.manager.emit("a.txt", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()

        arrangement.coordinator.cancel(arrangement.id("a.txt"))
        arrangement.coordinator.retry(arrangement.id("a.txt"))
        advanceUntilIdle()

        assertEquals(CellUploadState.Completed, arrangement.state("a.txt"))
        assertTrue(arrangement.manager.cancelled.isEmpty())
        arrangement.close()
    }

    @Test
    fun given_dismiss_then_only_finished_items_are_removed() = runTest {
        val arrangement = Arrangement(this, maxConcurrentUploads = 1)
        arrangement.coordinator.enqueue(listOf(request("a.txt"), request("b.txt")))
        advanceUntilIdle()

        arrangement.coordinator.dismiss(arrangement.id("a.txt"))
        arrangement.coordinator.dismiss(arrangement.id("b.txt"))
        advanceUntilIdle()
        assertEquals(2, arrangement.coordinator.uploads.value.size)

        arrangement.manager.emit("a.txt", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()
        arrangement.coordinator.dismiss(arrangement.id("a.txt"))
        advanceUntilIdle()

        assertEquals(listOf("b.txt"), arrangement.coordinator.uploads.value.map { it.fileName })
        arrangement.close()
    }

    @Test
    fun given_draft_creation_fails_then_item_is_failed_and_slot_released() = runTest {
        val arrangement = Arrangement(this, maxConcurrentUploads = 1)
        arrangement.manager.failUploadFor += "a.txt"
        arrangement.coordinator.enqueue(listOf(request("a.txt"), request("b.txt")))
        advanceUntilIdle()

        assertEquals(CellUploadState.Failed, arrangement.state("a.txt"))
        assertEquals(CellUploadState.Uploading(), arrangement.state("b.txt"))
        arrangement.close()
    }

    @Test
    fun given_publish_fails_then_item_is_failed() = runTest {
        val arrangement = Arrangement(this)
        arrangement.repository.publishFails = true
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()

        arrangement.manager.emit("a.txt", CellUploadEvent.UploadCompleted)
        advanceUntilIdle()

        assertEquals(CellUploadState.Failed, arrangement.state("a.txt"))
        assertEquals(1, arrangement.repository.publishDraftsCalls)
        arrangement.close()
    }

    @Test
    fun given_manager_already_dropped_finished_upload_then_item_is_published_and_completed() = runTest {
        val arrangement = Arrangement(this)
        arrangement.manager.dropFlowAfterUpload += "a.txt"
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()

        assertEquals(CellUploadState.Completed, arrangement.state("a.txt"))
        assertEquals(1, arrangement.repository.publishDraftsCalls)
        arrangement.close()
    }

    @Test
    fun given_upload_failed_before_subscription_then_item_is_failed() = runTest {
        val arrangement = Arrangement(this)
        arrangement.manager.failedBeforeSubscription += "a.txt"
        arrangement.coordinator.enqueue(listOf(request("a.txt")))
        advanceUntilIdle()

        assertEquals(CellUploadState.Failed, arrangement.state("a.txt"))
        arrangement.close()
    }

    private fun request(fileName: String, size: Long = 42L) = CellUploadRequest(
        localPath = "/tmp/$fileName".toPath(),
        fileName = fileName,
        sizeBytes = size,
        destinationFolderPath = "cells/folder",
    )

    private class Arrangement(
        testScope: TestScope,
        maxConcurrentUploads: Int = 3,
        largeFileThresholdBytes: Long = 100L * 1024 * 1024,
    ) {
        private val scope = CoroutineScope(testScope.coroutineContext + Job())
        val manager = FakeCellUploadManager()
        val repository = FakeCellsRepository()
        val coordinator = CellUploadCoordinatorImpl(
            manager, repository, scope, maxConcurrentUploads, largeFileThresholdBytes
        )

        fun state(fileName: String): CellUploadState =
            coordinator.uploads.value.first { it.fileName == fileName }.state

        fun id(fileName: String): String = coordinator.uploads.value.first { it.fileName == fileName }.id

        fun close() = scope.cancel()
    }

    private class FakeCellsRepository : CellsRepository {
        var publishDraftsCalls = 0
        var publishFails = false

        override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit> {
            publishDraftsCalls += 1
            return if (publishFails) NetworkFailure.NoNetworkConnection(null).left() else Unit.right()
        }

        override suspend fun preCheck(nodePath: String): Either<NetworkFailure, com.wire.kalium.cells.domain.model.PreCheckResult> = throw NotImplementedError()
        override suspend fun downloadFile(out: Path, cellPath: String, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun uploadFile(path: Path, node: CellNode, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun getPaginatedNodes(
            path: String?,
            query: String,
            limit: Int,
            offset: Int,
            fileFilters: FileFilters,
            sortingSpec: SortingSpec,
            isRecursive: Boolean,
        ): Either<NetworkFailure, com.wire.kalium.cells.domain.model.PaginatedList<CellNode>> = throw NotImplementedError()

        override suspend fun getNodesByPath(
            query: String,
            path: String,
            fileFilters: FileFilters,
            sortingSpec: SortingSpec,
        ): Either<NetworkFailure, List<CellNode>> = throw NotImplementedError()

        override suspend fun deleteFile(nodeUuid: String, permanentDelete: Boolean): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun cancelDraft(nodeUuid: String, versionUuid: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun getPreviews(nodeUuid: String): Either<NetworkFailure, List<NodePreview>?> = throw NotImplementedError()
        override suspend fun getNode(nodeUuid: String): Either<NetworkFailure, CellNode> = throw NotImplementedError()
        override suspend fun deleteFiles(paths: List<String>, permanentDelete: Boolean): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun createPublicLink(nodeUuid: String, fileName: String): Either<NetworkFailure, PublicLink> = throw NotImplementedError()
        override suspend fun getPublicLink(linkUuid: String): Either<NetworkFailure, PublicLink> = throw NotImplementedError()
        override suspend fun deletePublicLink(linkUuid: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun createPublicLinkPassword(linkUuid: String, password: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun updatePublicLinkPassword(linkUuid: String, password: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun removePublicLinkPassword(linkUuid: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun createFolder(folderName: String): Either<NetworkFailure, List<CellNode>> = throw NotImplementedError()
        override suspend fun createFile(folderName: String, contentType: String, templateUuid: String): Either<NetworkFailure, List<CellNode>> = throw NotImplementedError()
        override suspend fun moveNode(uuid: String, path: String, targetPath: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun renameNode(uuid: String, path: String, targetPath: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun restoreNode(uuid: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun getAllTags(): Either<NetworkFailure, List<String>> = throw NotImplementedError()
        override suspend fun updateNodeTags(uuid: String, tags: List<String>): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun removeNodeTags(uuid: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun getPublicLinkPassword(linkUuid: String): Either<StorageFailure, String?> = throw NotImplementedError()
        override suspend fun savePublicLinkPassword(linkUuid: String, password: String) = throw NotImplementedError()
        override suspend fun clearPublicLinkPassword(linkUuid: String) = throw NotImplementedError()
        override suspend fun setPublicLinkExpiration(linkUuid: String, expiresAt: Long?): Either<NetworkFailure, Unit> = throw NotImplementedError()
        override suspend fun getEditorUrl(nodeUuid: String, urlKey: String): Either<NetworkFailure, String> = throw NotImplementedError()
        override suspend fun getNodeVersions(uuid: String): Either<NetworkFailure, List<NodeVersion>> = throw NotImplementedError()
        override suspend fun restoreNodeVersion(uuid: String, versionId: String): Either<NetworkFailure, Unit> = throw NotImplementedError()
    }

    private class FakeCellUploadManager : CellUploadManager {
        private val flows = linkedMapOf<String, MutableSharedFlow<CellUploadEvent>>()
        private val infos = linkedMapOf<String, CellUploadInfo>()
        val failUploadFor = mutableSetOf<String>()
        val dropFlowAfterUpload = mutableSetOf<String>()
        val failedBeforeSubscription = mutableSetOf<String>()
        val cancelled = mutableListOf<String>()
        var uploadCalls = 0
        var retryUploadCalls = 0

        fun emit(fileName: String, event: CellUploadEvent) {
            flows.getValue("node-$fileName").tryEmit(event)
        }

        fun forget(fileName: String) {
            flows.remove("node-$fileName")
            infos.remove("node-$fileName")
        }

        override suspend fun upload(
            localPath: Path,
            assetSize: Long,
            destNodePath: String,
        ): Either<NetworkFailure, CellNode> {
            uploadCalls += 1
            val fileName = destNodePath.substringAfterLast('/')
            if (fileName in failUploadFor) return NetworkFailure.NoNetworkConnection(null).left()
            val uuid = "node-$fileName"
            if (fileName !in dropFlowAfterUpload) {
                flows[uuid] = MutableSharedFlow(extraBufferCapacity = 16)
                infos[uuid] = CellUploadInfo(progress = 0f, uploadFailed = fileName in failedBeforeSubscription)
            }
            return CellNode(
                uuid = uuid,
                versionId = "version-$fileName",
                path = destNodePath,
                size = assetSize,
                isDraft = true,
            ).right()
        }

        override fun observeUpload(nodeUuid: String): Flow<CellUploadEvent>? = flows[nodeUuid]

        override fun retryUpload(nodeUuid: String) {
            retryUploadCalls += 1
            infos[nodeUuid] = CellUploadInfo(progress = 0f, uploadFailed = false)
        }

        override suspend fun cancelUpload(nodeUuid: String) {
            cancelled += nodeUuid
        }

        override fun getUploadInfo(nodeUuid: String): CellUploadInfo? = infos[nodeUuid]

        override fun isUploading(nodeUuid: String): Boolean = flows.containsKey(nodeUuid)
    }
}
