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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.NetworkFailure.ServerMiscommunication
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.retry
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.localPath
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * Use case to refresh the cell asset state and preview.
 * - Check if the asset is still available.
 * - Remove local data if the asset is not found.
 * - Remove local data if the asset is updated (based on contentHash).
 * - Fetch preview URL with retries.
 */
public interface RefreshCellAssetStateUseCase {
    public suspend operator fun invoke(assetId: String): Either<CoreFailure, Unit>
}

internal class RefreshCellAssetStateUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : RefreshCellAssetStateUseCase {

    private companion object {
        private const val MAX_PREVIEW_FETCH_RETRIES = 20
        private const val DELAY = 500L
    }

    override suspend fun invoke(assetId: String): Either<CoreFailure, Unit> {
        return cellsRepository.getNode(assetId)
            .onSuccess { node ->
                if (node.isRecycled) {
                    removeLocalAssetData(assetId)
                } else {
                    refreshLocalData(node)
                }
            }
            .onFailure { error ->
                if (error.isAssetNotFound()) {
                    removeLocalAssetData(assetId)
                }
            }.map { node ->
                if (node.isPreviewSupported() && node.isRecycled.not()) {
                    getNodePreviews(node)
                        .onSuccess { previews ->
                            previews.maxBy { it.dimension }.let { preview ->
                                attachmentsRepository.savePreviewUrl(assetId, preview.url)
                            }
                        }
                }
            }
    }

    private suspend fun getNodePreviews(node: CellNode) =
        if (node.previews.isNotEmpty()) {
            node.previews.right()
        } else {
            retry(MAX_PREVIEW_FETCH_RETRIES, DELAY) {
                cellsRepository.getPreviews(node.uuid)
                    .onFailure { error ->
                        if (error.isAssetNotFound()) {
                            return error.left()
                        }
                    }
                    .flatMap { response ->
                        when {
                            response.isEmpty() -> StorageFailure.DataNotFound.left()
                            else -> response.right()
                        }
                    }
            }
        }

    private suspend fun removeLocalAssetData(assetId: String) {
        attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_FOUND)
        attachmentsRepository.getAttachment(assetId).map { attachment ->
            attachment.localPath()?.takeIf { it.isNotBlank() }?.let { localPath ->
                fileSystem.delete(localPath.toPath())
            }
        }
    }

    private suspend fun refreshLocalData(node: CellNode) {

        val attachment = attachmentsRepository
            .getAttachment(node.uuid).getOrNull() as? CellAssetContent
            ?: return

        var localPath = attachment.localPath

        // Check if asset was updated
        if (attachment.contentHash != node.contentHash) {
            localPath?.let { fileSystem.delete(it.toPath()) }
            attachmentsRepository.saveLocalPath(attachment.id, null)
            localPath = null
        }

        // Check if local file is still available
        localPath?.toPath()?.let {
            if (!fileSystem.exists(it)) {
                attachmentsRepository.saveLocalPath(attachment.id, null)
                localPath = null
            }
        }

        attachmentsRepository.updateAttachment(attachment.id, node.contentUrl, node.contentHash, node.path)

        // Update transfer status for attachments previously marked as NOT_FOUND
        // This happens after we regain access to the file
        if (attachment.transferStatus == AssetTransferStatus.NOT_FOUND) {
            if (localPath == null) {
                attachmentsRepository.setAssetTransferStatus(attachment.id, AssetTransferStatus.NOT_DOWNLOADED)
            } else {
                attachmentsRepository.setAssetTransferStatus(attachment.id, AssetTransferStatus.SAVED_INTERNALLY)
            }
        }
    }
}

@Suppress("ReturnCount")
internal fun NetworkFailure.isAssetNotFound(): Boolean {
    val error = (this as? ServerMiscommunication)?.kaliumException ?: return false
    val response = (error as? KaliumException.ServerError)?.errorResponse ?: return false
    return response.code == HttpStatusCode.NotFound.value || response.code == HttpStatusCode.Forbidden.value
}

// TODO: Will be later replaced with a flag from Cell Server
private fun CellNode.isPreviewSupported(): Boolean = when {
    mimeType == null -> false
    mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType == "application/pdf" -> true
    else -> false
}
