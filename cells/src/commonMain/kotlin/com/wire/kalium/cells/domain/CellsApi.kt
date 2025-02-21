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
import com.wire.kalium.cells.data.model.GetFilesResponseDTO
import com.wire.kalium.cells.data.model.PreCheckResultDTO
import com.wire.kalium.network.utils.NetworkResponse

internal interface CellsApi {
    suspend fun preCheck(path: String): NetworkResponse<PreCheckResultDTO>
    suspend fun cancelDraft(nodeUuid: String, versionUuid: String): NetworkResponse<Unit>
    suspend fun publishDraft(nodeUuid: String): NetworkResponse<Unit>
    suspend fun delete(node: CellNodeDTO): NetworkResponse<Unit>
    suspend fun getFiles(cellName: String): NetworkResponse<GetFilesResponseDTO>
    suspend fun createPublicUrl(uuid: String, fileName: String): NetworkResponse<String>
}
