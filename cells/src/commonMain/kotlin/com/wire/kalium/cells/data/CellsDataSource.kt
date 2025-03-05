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

import com.wire.kalium.cells.data.model.toDto
import com.wire.kalium.cells.data.model.toModel
import com.wire.kalium.cells.domain.CellsApi
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentsDao
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.use

internal class CellsDataSource internal constructor(
    private val cellsApi: CellsApi,
    private val awsClient: CellsAwsClient,
    private val messageAttachments: MessageAttachmentsDao,
    private val fileSystem: FileSystem,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : CellsRepository {

    override suspend fun preCheck(nodePath: String): Either<NetworkFailure, PreCheckResult> {
        return withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.preCheck(nodePath)
            }.map { result ->
                if (result.fileExists) {
                    PreCheckResult.FileExists(result.nextPath ?: nodePath)
                } else {
                    PreCheckResult.Success
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun uploadFile(
        path: Path,
        node: CellNode,
        onProgressUpdate: (Long) -> Unit,
    ): Either<NetworkFailure, Unit> = withContext(dispatchers.io) {
        try {
            awsClient.upload(path, node.toDto(), onProgressUpdate)
            Either.Right(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Either.Left(NetworkFailure.ServerMiscommunication(e))
        }
    }

    override suspend fun getFiles(cellName: String): Either<NetworkFailure, List<CellNode>> =
        wrapApiRequest {
            cellsApi.getFiles(cellName)
        }.map { response ->
            response.nodes
                .filterNot { it.isRecycleBin }
                .map { it.toModel() }
        }

    override suspend fun deleteFile(node: CellNode): Either<NetworkFailure, Unit> =
        wrapApiRequest {
            cellsApi.delete(node.toDto())
        }

    override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit> =
        coroutineScope {
            nodes.map { (nodeId, versionId) ->
                async {
                    wrapApiRequest { cellsApi.publishDraft(nodeId, versionId) }
                }
            }.awaitAll().firstOrNull { it.isLeft() } ?: Unit.right()
        }

    override suspend fun cancelDraft(nodeUuid: String, versionUuid: String): Either<NetworkFailure, Unit> =
        wrapApiRequest {
            cellsApi.cancelDraft(nodeUuid, versionUuid)
        }

    override suspend fun savePreviewUrl(assetId: String, url: String) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachments.setPreviewUrl(assetId, url)
        }
    }

    override suspend fun getAssetPath(assetId: String): Either<StorageFailure, String?> = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachments.getAssetPath(assetId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun downloadFile(out: Path, cellPath: String, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit> {
        return try {
            fileSystem.sink(out, true).use { sink ->
                awsClient.download(cellPath, sink, onProgressUpdate)
                Either.Right(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Either.Left(NetworkFailure.ServerMiscommunication(e))
        }
    }

    override suspend fun setAssetTransferStatus(assetId: String, status: AssetTransferStatus): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            messageAttachments.setTransferStatus(assetId, status.name)
        }

    override suspend fun saveLocalPath(assetId: String, path: String): Either<StorageFailure, Unit> = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachments.setLocalPath(assetId, path)
        }
    }

    override suspend fun getPreviews(nodeUuid: String): Either<NetworkFailure, List<NodePreview>> =
        wrapApiRequest {
            cellsApi.getNode(nodeUuid).mapSuccess { response ->
                response.previews.map { preview ->
                    NodePreview(
                        preview.url,
                        preview.dimension ?: 0,
                    )
                }
            }
        }
}
