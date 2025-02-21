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

import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.coroutines.flow.Flow

internal interface MessageAttachmentDraftRepository {
    suspend fun add(conversationId: QualifiedID, node: CellNode, dataPath: String): Either<CoreFailure, Unit>
    suspend fun get(uuid: String): Either<CoreFailure, AttachmentDraft?>
    suspend fun getAll(conversationId: ConversationId): Either<CoreFailure, List<AttachmentDraft>>
    suspend fun observe(conversationId: QualifiedID): Flow<List<AttachmentDraft>>
    suspend fun updateStatus(uuid: String, status: AttachmentUploadStatus): Either<CoreFailure, Unit>
    suspend fun remove(uuid: String): Either<CoreFailure, Unit>
}
