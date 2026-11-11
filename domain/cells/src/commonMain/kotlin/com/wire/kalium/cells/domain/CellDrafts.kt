/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import okio.Path

/** Uploading files as drafts, then publishing or cancelling them: Cells API calls only, without the account's database. */
internal interface CellDrafts {
    suspend fun preCheck(nodePath: String): Either<NetworkFailure, PreCheckResult>
    suspend fun uploadFile(path: Path, node: CellNode, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit>
    suspend fun cancelDraft(nodeUuid: String, versionUuid: String): Either<NetworkFailure, Unit>
    suspend fun getNodeVersions(uuid: String): Either<NetworkFailure, List<NodeVersion>>
    suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit>
}
