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

import kotlin.uuid.Uuid
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadInfo
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.SYSTEM

internal class CellUploadManagerImpl internal constructor(
    private val repository: CellsRepository,
    private val uploadScope: CoroutineScope,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : CellUploadManager {

    private val uploads = mutableMapOf<String, UploadInfo>()

    override suspend fun upload(
        localPath: Path,
        assetSize: Long,
        destNodePath: String,
    ): Either<NetworkFailure, CellNode> =
        repository.preCheck(destNodePath).map { result ->

            val path = when (result) {
                is PreCheckResult.FileExists -> result.nextPath
                is PreCheckResult.Success -> destNodePath
            }

            CellNode(
                uuid = Uuid.random().toString(),
                versionId = Uuid.random().toString(),
                path = path,
                size = assetSize,
                isDraft = true,
            ).also {
                startUpload(localPath, it)
            }
        }

    private fun startUpload(localPath: Path, node: CellNode) {

        val uploadEventsFlow = MutableSharedFlow<CellUploadEvent>()

        val uploadJob = uploadScope.launch {
            repository.uploadFile(
                path = localPath,
                node = node,
                onProgressUpdate = { updateUploadProgress(node.uuid, it) }
            )
                .onSuccess {
                    uploads.remove(node.uuid)
                    uploadEventsFlow.emit(CellUploadEvent.UploadCompleted)
                }
                .onFailure {
                    updateJobInfo(node.uuid) { copy(uploadFailed = true) }
                    uploadEventsFlow.emit(CellUploadEvent.UploadError)
                }
        }

        uploads[node.uuid] = UploadInfo(
            node = node,
            job = uploadJob,
            localPath = localPath,
            events = uploadEventsFlow,
        )
    }

    override fun retryUpload(nodeUuid: String) {
        uploads[nodeUuid]?.let { uploadInfo ->
            if (fileSystem.exists(uploadInfo.localPath)) {
                startUpload(uploadInfo.localPath, uploadInfo.node)
            } else {
                // Original file is not available
                updateJobInfo(nodeUuid) { copy(uploadFailed = true) }
                uploadScope.launch { uploadInfo.events.emit(CellUploadEvent.UploadError) }
            }
        }
    }

    private fun updateUploadProgress(nodeUuid: String, uploaded: Long) {
        uploads[nodeUuid]?.let { info ->
            val progress = info.node.size?.let { uploaded.toFloat() / it } ?: 0f
            updateJobInfo(info.node.uuid) { copy(progress = progress) }
            uploadScope.launch {
                info.events.emit(CellUploadEvent.UploadProgress(progress))
            }
        }
    }

    override suspend fun cancelUpload(nodeUuid: String) {
        uploads[nodeUuid]?.run {
            events.emit(CellUploadEvent.UploadCancelled)
            job.cancelAndJoin()
            uploads.remove(nodeUuid)
        }
    }

    override fun observeUpload(nodeUuid: String): Flow<CellUploadEvent>? {
        return uploads[nodeUuid]?.events?.asSharedFlow()
    }

    override fun getUploadInfo(nodeUuid: String): CellUploadInfo? {
        return uploads[nodeUuid]?.run {
            CellUploadInfo(
                progress = progress,
                uploadFailed = uploadFailed,
            )
        }
    }

    override fun isUploading(nodeUuid: String): Boolean {
        return uploads.containsKey(nodeUuid)
    }

    private fun updateJobInfo(uuid: String, block: UploadInfo.() -> UploadInfo) {
        uploads[uuid]?.let { uploads[uuid] = block(it) }
    }
}

private data class UploadInfo(
    val node: CellNode,
    val localPath: Path,
    val job: Job,
    val events: MutableSharedFlow<CellUploadEvent>,
    val progress: Float = 0f,
    val uploadFailed: Boolean = false,
)
