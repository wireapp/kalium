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
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.cells.domain.model.PaginatedList
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import okio.Path

class FakeCellsRepository : CellsRepository {
    var requestedUuid: String? = null
    var invocationCount: Int = 0

    var result: Either<NetworkFailure, List<NodeVersion>> =
        Either.Right(emptyList())


    override suspend fun preCheck(nodePath: String): Either<NetworkFailure, PreCheckResult> {
        TODO("Not yet implemented")
    }

    override suspend fun downloadFile(
        out: Path,
        cellPath: String,
        onProgressUpdate: (Long) -> Unit
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun uploadFile(
        path: Path,
        node: CellNode,
        onProgressUpdate: (Long) -> Unit
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun getPaginatedNodes(
        path: String?,
        query: String,
        limit: Int,
        offset: Int,
        onlyDeleted: Boolean,
        tags: List<String>
    ): Either<NetworkFailure, PaginatedList<CellNode>> {
        TODO("Not yet implemented")
    }

    override suspend fun getNodesByPath(
        path: String,
        onlyFolders: Boolean
    ): Either<NetworkFailure, List<CellNode>> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteFile(
        nodeUuid: String,
        permanentDelete: Boolean
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun cancelDraft(
        nodeUuid: String,
        versionUuid: String
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun getPreviews(nodeUuid: String): Either<NetworkFailure, List<NodePreview>> {
        TODO("Not yet implemented")
    }

    override suspend fun getNode(nodeUuid: String): Either<NetworkFailure, CellNode> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteFiles(
        paths: List<String>,
        permanentDelete: Boolean
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun createPublicLink(
        nodeUuid: String,
        fileName: String
    ): Either<NetworkFailure, PublicLink> {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicLink(linkUuid: String): Either<NetworkFailure, PublicLink> {
        TODO("Not yet implemented")
    }

    override suspend fun deletePublicLink(linkUuid: String): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun createPublicLinkPassword(
        linkUuid: String,
        password: String
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun updatePublicLinkPassword(
        linkUuid: String,
        password: String
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun removePublicLinkPassword(linkUuid: String): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun createFolder(folderName: String): Either<NetworkFailure, List<CellNode>> {
        TODO("Not yet implemented")
    }

    override suspend fun moveNode(
        uuid: String,
        path: String,
        targetPath: String
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun renameNode(
        uuid: String,
        path: String,
        targetPath: String
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun restoreNode(uuid: String): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun getAllTags(): Either<NetworkFailure, List<String>> {
        TODO("Not yet implemented")
    }

    override suspend fun updateNodeTags(
        uuid: String,
        tags: List<String>
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun removeNodeTags(uuid: String): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicLinkPassword(linkUuid: String): Either<StorageFailure, String?> {
        TODO("Not yet implemented")
    }

    override suspend fun savePublicLinkPassword(linkUuid: String, password: String) {
        TODO("Not yet implemented")
    }

    override suspend fun clearPublicLinkPassword(linkUuid: String) {
        TODO("Not yet implemented")
    }

    override suspend fun setPublicLinkExpiration(
        linkUuid: String,
        expiresAt: Long?
    ): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun getNodeVersions(uuid: String): Either<NetworkFailure, List<NodeVersion>> {
        requestedUuid = uuid
        invocationCount++
        return result
    }
}
