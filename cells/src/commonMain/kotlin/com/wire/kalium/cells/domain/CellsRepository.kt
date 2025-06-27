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
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.cells.domain.model.PaginatedList
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import io.mockative.Mockable
import okio.Path

@Suppress("TooManyFunctions")
@Mockable
internal interface CellsRepository {
    suspend fun preCheck(nodePath: String): Either<NetworkFailure, PreCheckResult>
    suspend fun downloadFile(out: Path, cellPath: String, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit>
    suspend fun uploadFile(path: Path, node: CellNode, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit>
    suspend fun getPaginatedNodes(
        path: String?,
        query: String,
        limit: Int,
        offset: Int,
        onlyDeleted: Boolean = false,
        tags: List<String> = emptyList()
    ): Either<NetworkFailure, PaginatedList<CellNode>>

    suspend fun getNodesByPath(
        path: String,
        onlyFolders: Boolean
    ): Either<NetworkFailure, List<CellNode>>

    suspend fun deleteFile(nodeUuid: String): Either<NetworkFailure, Unit>
    suspend fun cancelDraft(nodeUuid: String, versionUuid: String): Either<NetworkFailure, Unit>
    suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit>
    suspend fun getPreviews(nodeUuid: String): Either<NetworkFailure, List<NodePreview>>
    suspend fun getNode(nodeUuid: String): Either<NetworkFailure, CellNode>
    suspend fun deleteFiles(paths: List<String>): Either<NetworkFailure, Unit>
    suspend fun createPublicLink(nodeUuid: String, fileName: String): Either<NetworkFailure, PublicLink>
    suspend fun getPublicLink(linkUuid: String): Either<NetworkFailure, String>
    suspend fun deletePublicLink(linkUuid: String): Either<NetworkFailure, Unit>
    suspend fun createFolder(folderName: String): Either<NetworkFailure, List<CellNode>>
    suspend fun moveNode(uuid: String, path: String, targetPath: String): Either<NetworkFailure, Unit>
    suspend fun restoreNode(path: String): Either<NetworkFailure, Unit>
    suspend fun getAllTags(): Either<NetworkFailure, List<String>>
}
