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

import com.wire.kalium.cells.data.model.CellNodeDTO
import com.wire.kalium.cells.data.model.GetNodesResponseDTO
import com.wire.kalium.cells.data.model.PreCheckResultDTO
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.network.utils.NetworkResponse

@Suppress("TooManyFunctions")
internal interface CellsApi {
    suspend fun getNode(uuid: String): NetworkResponse<CellNodeDTO>
    suspend fun preCheck(path: String): NetworkResponse<PreCheckResultDTO>
    suspend fun cancelDraft(nodeUuid: String, versionUuid: String): NetworkResponse<Unit>
    suspend fun publishDraft(nodeUuid: String, versionId: String): NetworkResponse<Unit>
    suspend fun delete(nodeUuid: String): NetworkResponse<Unit>
    suspend fun getNodes(query: String, limit: Int, offset: Int): NetworkResponse<GetNodesResponseDTO>
    suspend fun createPublicLink(uuid: String, fileName: String): NetworkResponse<PublicLink>
    suspend fun delete(paths: List<String>): NetworkResponse<Unit>
    suspend fun deletePublicLink(linkUuid: String): NetworkResponse<Unit>
    suspend fun getPublicLink(linkUuid: String): NetworkResponse<String>
    suspend fun getNodesForPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        onlyDeleted: Boolean = false,
        onlyFolders: Boolean = false
    ): NetworkResponse<GetNodesResponseDTO>

    suspend fun createFolder(path: String): NetworkResponse<GetNodesResponseDTO>
    suspend fun moveNode(
        uuid: String,
        path: String,
        targetPath: String,
        targetIsParent: Boolean = true
    ): NetworkResponse<Unit>
    suspend fun restoreNode(path: String): NetworkResponse<Unit>
}
