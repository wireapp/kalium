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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadAttempt
import com.wire.kalium.cells.domain.CellUploadInfo
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

internal class CellUploadManagerImpl internal constructor(
    private val repository: CellsRepository,
    private val uploadScope: CoroutineScope,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : CellUploadManager {

    // Uploads are registered and completed on concurrent workers, including native draft owners.
    private val uploads = MutableStateFlow<Map<String, UploadInfo>>(emptyMap())
    private val outcomes = CellUploadOutcomes()

    override suspend fun upload(
        localPath: Path,
        assetSize: Long,
        destNodePath: String,
    ): Either<NetworkFailure, CellNode> =
        repository.prepareUploadNode(destNodePath, assetSize).map { node ->
            node.also { startUpload(localPath, it) }
        }

    private fun startUpload(localPath: Path, node: CellNode) {
        outcomes.clear(node.uuid)

        val uploadEventsFlow = MutableSharedFlow<CellUploadEvent>()

        val uploadJob = uploadScope.launch(start = CoroutineStart.LAZY) {
            repository.uploadFile(
                path = localPath,
                node = node,
                onProgressUpdate = { updateUploadProgress(node.uuid, it) }
            )
                .onSuccess {
                    outcomes.record(node.uuid, CellUploadEvent.UploadCompleted)
                    uploads.update { it - node.uuid }
                    uploadEventsFlow.emit(CellUploadEvent.UploadCompleted)
                }
                .onFailure { failure ->
                    outcomes.record(node.uuid, CellUploadEvent.UploadError)
                    updateJobInfo(node.uuid) {
                        copy(uploadFailed = true, isConnectivityIssue = failure is NetworkFailure.NoNetworkConnection)
                    }
                    uploadEventsFlow.emit(CellUploadEvent.UploadError)
                }
        }

        val info = UploadInfo(
            node = node,
            job = uploadJob,
            localPath = localPath,
            events = uploadEventsFlow,
        )
        uploads.update { it + (node.uuid to info) }
        uploadJob.start()
    }

    override fun retryUpload(nodeUuid: String) {
        uploads.value[nodeUuid]?.let { uploadInfo ->
            if (fileSystem.exists(uploadInfo.localPath)) {
                startUpload(uploadInfo.localPath, uploadInfo.node)
            } else {
                // Original file is not available. Not a connectivity issue even if the last attempt was:
                // explicit reset, since copy() would otherwise keep that stale flag from the previous failure.
                updateJobInfo(nodeUuid) { copy(uploadFailed = true, isConnectivityIssue = false) }
                uploadScope.launch { uploadInfo.events.emit(CellUploadEvent.UploadError) }
            }
        }
    }

    override fun retryDraftUpload(draft: AttachmentDraft): Boolean {
        val path = draft.localFilePath.toPath()
        val retained = uploads.value[draft.uuid]
        if (draft.versionId.isBlank() || !fileSystem.exists(path) || retained?.uploadFailed == false) return false
        val node = retained?.node ?: CellNode(
            uuid = draft.uuid,
            versionId = draft.versionId,
            path = draft.remoteFilePath,
            size = draft.fileSize,
            isDraft = true,
        )
        startUpload(path, node)
        return true
    }

    private fun updateUploadProgress(nodeUuid: String, uploaded: Long) {
        uploads.value[nodeUuid]?.let { info ->
            val progress = info.node.size?.let { uploaded.toFloat() / it } ?: 0f
            updateJobInfo(info.node.uuid) { copy(progress = progress) }
            uploadScope.launch {
                info.events.emit(CellUploadEvent.UploadProgress(progress))
            }
        }
    }

    override suspend fun cancelUpload(nodeUuid: String) {
        uploads.value[nodeUuid]?.let { cancelAttempt(nodeUuid, it) }
    }

    override fun captureUploadAttempt(nodeUuid: String): CellUploadAttempt? = uploads.value[nodeUuid]?.let { captured ->
        object : CellUploadAttempt {
            override suspend fun awaitStopped() { captured.job.join() }
            override suspend fun cancelAndAwait() { cancelAttempt(nodeUuid, captured) }
        }
    }

    private suspend fun cancelAttempt(nodeUuid: String, captured: UploadInfo) {
        if (uploads.value[nodeUuid]?.job === captured.job) outcomes.record(nodeUuid, CellUploadEvent.UploadCancelled)
        captured.events.emit(CellUploadEvent.UploadCancelled)
        captured.job.cancelAndJoin()
        uploads.update { current ->
            if (current[nodeUuid]?.job === captured.job) current - nodeUuid else current
        }
    }

    override fun consumeUploadOutcome(nodeUuid: String): CellUploadEvent? = outcomes.consume(nodeUuid)

    override fun observeUpload(nodeUuid: String): Flow<CellUploadEvent>? {
        return uploads.value[nodeUuid]?.events?.asSharedFlow()
    }

    override fun getUploadInfo(nodeUuid: String): CellUploadInfo? {
        return uploads.value[nodeUuid]?.run {
            CellUploadInfo(
                progress = progress,
                uploadFailed = uploadFailed,
                isConnectivityIssue = isConnectivityIssue,
            )
        }
    }

    override fun isUploading(nodeUuid: String): Boolean {
        return uploads.value.containsKey(nodeUuid)
    }

    private fun updateJobInfo(uuid: String, block: UploadInfo.() -> UploadInfo) {
        uploads.update { current ->
            current[uuid]?.let { current + (uuid to block(it)) } ?: current
        }
    }
}

private data class UploadInfo(
    val node: CellNode,
    val localPath: Path,
    val job: Job,
    val events: MutableSharedFlow<CellUploadEvent>,
    val progress: Float = 0f,
    val uploadFailed: Boolean = false,
    val isConnectivityIssue: Boolean = false,
)

private class CellUploadOutcomes {
    private val values = MutableStateFlow<Map<String, CellUploadEvent>>(emptyMap())

    fun clear(nodeUuid: String) { values.update { it - nodeUuid } }

    fun consume(nodeUuid: String): CellUploadEvent? {
        var result: CellUploadEvent? = null
        values.update { current ->
            result = current[nodeUuid]
            current - nodeUuid
        }
        return result
    }

    fun record(nodeUuid: String, event: CellUploadEvent) {
        // Draft waiters consume entries; bound outcomes unused by Drive callers for this session.
        values.update { current ->
            (current + (nodeUuid to event)).entries.toList().takeLast(MAX_RECORDED_OUTCOMES)
                .associate { it.key to it.value }
        }
    }
}

private const val MAX_RECORDED_OUTCOMES = 128
