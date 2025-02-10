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

import com.benasher44.uuid.uuid4
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadInfo
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okio.Path

internal class CellUploadManagerImpl internal constructor(
    private val repository: CellsRepository,
    private val uploadScope: CoroutineScope,
) : CellUploadManager {

    private val uploads = mutableMapOf<String, UploadInfo>()

    override suspend fun upload(
        assetPath: Path,
        assetSize: Long,
        destNodePath: String,
    ): Either<NetworkFailure, CellNode> =
        repository.preCheck(destNodePath).map { result ->

            val path = when (result) {
                is PreCheckResult.FileExists -> result.nextPath
                is PreCheckResult.Success -> destNodePath
            }

            CellNode(
                uuid = uuid4().toString(),
                versionId = uuid4().toString(),
                path = path,
                size = assetSize,
                isDraft = true,
            ).also {
                startUpload(assetPath, it)
            }
        }

    private fun startUpload(assetPath: Path, node: CellNode) {

        val uploadEventsFlow = MutableSharedFlow<CellUploadEvent>()

        val uploadJob = uploadScope.launch {
            repository.uploadFile(
                path = assetPath,
                node = node,
                onProgressUpdate = { updateUploadProgress(node.uuid, it) }
            )
                .onSuccess {
                    uploads.remove(node.uuid)
                    uploadEventsFlow.emit(CellUploadEvent.UploadCompleted)
                }
                .onFailure {
                    updateJobInfo(node.uuid) { copy(uploadFiled = true) }
                    uploadEventsFlow.emit(CellUploadEvent.UploadError)
                }
        }

        uploads[node.uuid] = UploadInfo(
            node = node,
            job = uploadJob,
            events = uploadEventsFlow,
        )
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

    override fun cancelUpload(nodeUuid: String) {
        uploads[nodeUuid]?.run {
            job.cancel()
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
                uploadFiled = uploadFiled,
            )
        }
    }

    private fun updateJobInfo(uuid: String, block: UploadInfo.() -> UploadInfo) {
        uploads[uuid]?.let { uploads[uuid] = block(it) }
    }
}

private data class UploadInfo(
    val node: CellNode,
    val job: Job,
    val events: MutableSharedFlow<CellUploadEvent>,
    val progress: Float = 0f,
    val uploadFiled: Boolean = false,
)
