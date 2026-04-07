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
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.cells.domain.model.PaginatedList
import com.wire.kalium.cells.domain.model.Pagination
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.persistence.dao.publiclink.PublicLinkDao
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

@Suppress("TooManyFunctions")
internal class CellsDataSource internal constructor(
    private val cellsApi: CellsApi,
    private val publicLinkDao: PublicLinkDao,
    private val awsClient: CellsAwsClient,
    private val fileSystem: FileSystem,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : CellsRepository {

    override suspend fun preCheck(nodePath: String) = withContext(dispatchers.io) {
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

    override suspend fun getPaginatedNodes(
        path: String?,
        query: String,
        limit: Int,
        offset: Int,
        fileFilters: FileFilters,
        sortingSpec: SortingSpec,
    ) = withContext(dispatchers.io) {
        wrapApiRequest {
            if (path == null) {
                cellsApi.getNodes(
                    query = query,
                    limit = limit,
                    offset = offset,
                    fileFilters = fileFilters,
                    sortingSpec = sortingSpec,
                )
            } else {
                cellsApi.getNodesForPath(
                    query = query,
                    path = path,
                    limit = limit,
                    offset = offset,
                    fileFilters = fileFilters,
                    sortingSpec = sortingSpec,
                )
            }
        }.map { response ->
            PaginatedList(
                data = response.nodes.map { it.toModel() },
                pagination = response.pagination?.let {
                    Pagination(
                        nextOffset = it.nextOffset,
                    )
                },
            )
        }
    }

    override suspend fun getNodesByPath(
        query: String,
        path: String,
        fileFilters: FileFilters,
        sortingSpec: SortingSpec,
    ): Either<NetworkFailure, List<CellNode>> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.getNodesForPath(
                query = query,
                path = path,
                fileFilters = fileFilters,
                sortingSpec = sortingSpec
            ).mapSuccess { response ->
                response.nodes.map { it.toModel() }
            }
        }
    }

    override suspend fun deleteFile(nodeUuid: String, permanentDelete: Boolean) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.delete(nodeUuid, permanentDelete)
        }
    }

    override suspend fun deleteFiles(paths: List<String>, permanentDelete: Boolean) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.delete(paths, permanentDelete)
        }
    }

    override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>) = withContext(dispatchers.io) {
        coroutineScope {
            nodes.map { (nodeId, versionId) ->
                async {
                    wrapApiRequest { cellsApi.publishDraft(nodeId, versionId) }
                }
            }.awaitAll().firstOrNull { it.isLeft() } ?: Unit.right()
        }
    }

    override suspend fun cancelDraft(nodeUuid: String, versionUuid: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.cancelDraft(nodeUuid, versionUuid)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun downloadFile(out: Path, cellPath: String, onProgressUpdate: (Long) -> Unit) =
        try {
            fileSystem.sink(out, true).use { sink ->
                awsClient.download(cellPath, sink, onProgressUpdate)
                Either.Right(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Either.Left(NetworkFailure.ServerMiscommunication(e))
        }

    override suspend fun getPreviews(nodeUuid: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.getNode(nodeUuid).mapSuccess { response ->
                response.previews?.map { preview ->
                    NodePreview(
                        preview.url,
                        preview.dimension ?: 0,
                    )
                } ?: emptyList()
            }
        }
    }

    override suspend fun getNode(nodeUuid: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.getNode(nodeUuid).mapSuccess { it.toModel() }
        }
    }

    override suspend fun createPublicLink(nodeUuid: String, fileName: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.createPublicLink(nodeUuid, fileName)
        }
    }

    override suspend fun getPublicLink(linkUuid: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.getPublicLink(linkUuid)
        }
    }

    override suspend fun deletePublicLink(linkUuid: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.deletePublicLink(linkUuid)
        }
    }

    override suspend fun createPublicLinkPassword(linkUuid: String, password: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.createPublicLinkPassword(linkUuid, password)
        }
    }

    override suspend fun updatePublicLinkPassword(linkUuid: String, password: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.updatePublicLinkPassword(linkUuid, password)
        }
    }

    override suspend fun removePublicLinkPassword(linkUuid: String) = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.removePublicLinkPassword(linkUuid)
        }
    }

    override suspend fun createFolder(folderName: String): Either<NetworkFailure, List<CellNode>> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.createFolder(folderName)
        }.map { response ->
            response.nodes.map { it.toModel() }
        }
    }

    override suspend fun createFile(
        folderName: String,
        contentType: String,
        templateUuid: String
    ): Either<NetworkFailure, List<CellNode>> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.createFile(folderName, contentType, templateUuid)
        }.map { response ->
            response.nodes.map { it.toModel() }
        }
    }

    override suspend fun moveNode(uuid: String, path: String, targetPath: String): Either<NetworkFailure, Unit> =
        withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.moveNode(uuid = uuid, path = path, targetPath = targetPath)
            }
        }

    override suspend fun renameNode(uuid: String, path: String, targetPath: String): Either<NetworkFailure, Unit> =
        withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.renameNode(uuid = uuid, path = path, targetPath = targetPath)
            }
        }

    override suspend fun restoreNode(uuid: String): Either<NetworkFailure, Unit> =
        withContext(dispatchers.io) {
            wrapApiRequest {
                cellsApi.restoreNode(uuid = uuid)
            }
        }

    override suspend fun getAllTags(): Either<NetworkFailure, List<String>> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.getAllTags()
        }
    }

    override suspend fun updateNodeTags(uuid: String, tags: List<String>): Either<NetworkFailure, Unit> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.updateNodeTags(uuid = uuid, tags = tags)
        }
    }

    override suspend fun removeNodeTags(uuid: String): Either<NetworkFailure, Unit> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.removeTagsFromNode(uuid = uuid)
        }
    }

    override suspend fun getPublicLinkPassword(linkUuid: String): Either<StorageFailure, String?> = wrapStorageRequest {
        publicLinkDao.get(linkUuid)?.password
    }

    override suspend fun savePublicLinkPassword(linkUuid: String, password: String) {
        wrapStorageRequest {
            publicLinkDao.insert(linkUuid, password)
        }
    }

    override suspend fun clearPublicLinkPassword(linkUuid: String) {
        wrapStorageRequest {
            publicLinkDao.delete(linkUuid)
        }
    }

    override suspend fun setPublicLinkExpiration(linkUuid: String, expiresAt: Long?) = wrapApiRequest {
        cellsApi.setPublicLinkExpiration(linkUuid, expiresAt)
    }

    override suspend fun getEditorUrl(nodeUuid: String, urlKey: String) = wrapApiRequest {
        cellsApi.getNodeEditorUrl(nodeUuid, urlKey)
    }

    override suspend fun getNodeVersions(uuid: String): Either<NetworkFailure, List<NodeVersion>> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.getNodeVersions(uuid = uuid).mapSuccess { collection ->
                collection.map { node ->
                    node.toModel()
                }
            }
        }
    }

    override suspend fun restoreNodeVersion(
        uuid: String,
        versionId: String
    ): Either<NetworkFailure, Unit> = withContext(dispatchers.io) {
        wrapApiRequest {
            cellsApi.restoreNodeVersion(uuid, versionId)
        }
    }
}
