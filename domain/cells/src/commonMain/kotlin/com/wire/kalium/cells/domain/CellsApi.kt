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
import com.wire.kalium.cells.data.model.NodeVersionDTO
import com.wire.kalium.cells.data.model.PreCheckResultDTO
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.cells.sdk.kmp.model.RestFlag
import com.wire.kalium.cells.sdk.kmp.model.RestNodeVersionsFilter
import com.wire.kalium.cells.sdk.kmp.model.RestPromoteParameters
import com.wire.kalium.network.utils.NetworkResponse

@Suppress("TooManyFunctions", "LongParameterList")
internal interface CellsApi {
    suspend fun getNode(uuid: String): NetworkResponse<CellNodeDTO>
    suspend fun getNodes(query: String, limit: Int, offset: Int, tags: List<String>): NetworkResponse<GetNodesResponseDTO>
    suspend fun getNodesForPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        onlyDeleted: Boolean = false,
        onlyFolders: Boolean = false,
        tags: List<String> = emptyList()
    ): NetworkResponse<GetNodesResponseDTO>

    suspend fun getAllTags(): NetworkResponse<List<String>>
    suspend fun getPublicLink(linkUuid: String): NetworkResponse<PublicLink>
    suspend fun getNodeVersions(
        uuid: String,
        query: RestNodeVersionsFilter = RestNodeVersionsFilter(flags = listOf(RestFlag.WithPreSignedURLs))
    ): NetworkResponse<List<NodeVersionDTO>>

    suspend fun preCheck(path: String): NetworkResponse<PreCheckResultDTO>
    suspend fun cancelDraft(nodeUuid: String, versionUuid: String): NetworkResponse<Unit>
    suspend fun publishDraft(nodeUuid: String, versionId: String): NetworkResponse<Unit>
    suspend fun delete(nodeUuid: String, permanentDelete: Boolean = false): NetworkResponse<Unit>
    suspend fun createPublicLink(uuid: String, fileName: String): NetworkResponse<PublicLink>
    suspend fun delete(paths: List<String>, permanentDelete: Boolean = false): NetworkResponse<Unit>
    suspend fun deletePublicLink(linkUuid: String): NetworkResponse<Unit>

    suspend fun createFolder(path: String): NetworkResponse<GetNodesResponseDTO>
    suspend fun createPublicLinkPassword(linkUuid: String, password: String): NetworkResponse<Unit>
    suspend fun updatePublicLinkPassword(linkUuid: String, password: String): NetworkResponse<Unit>
    suspend fun updateNodeTags(uuid: String, tags: List<String>): NetworkResponse<Unit>
    suspend fun moveNode(
        uuid: String,
        path: String,
        targetPath: String,
    ): NetworkResponse<Unit>

    suspend fun renameNode(
        uuid: String,
        path: String,
        targetPath: String,
    ): NetworkResponse<Unit>

    suspend fun restoreNode(uuid: String): NetworkResponse<Unit>
    suspend fun restoreNodeVersion(
        uuid: String,
        versionId: String,
        restPromoteParameters: RestPromoteParameters = RestPromoteParameters()
    ): NetworkResponse<Unit>

    suspend fun removeTagsFromNode(uuid: String): NetworkResponse<Unit>
    suspend fun removePublicLinkPassword(linkUuid: String): NetworkResponse<Unit>
    suspend fun setPublicLinkExpiration(linkUuid: String, expireAt: Long?): NetworkResponse<Unit>
}
