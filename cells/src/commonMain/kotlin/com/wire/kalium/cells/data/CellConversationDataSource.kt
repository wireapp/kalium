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

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

internal class CellConversationDataSource(
    private val conversation: ConversationDAO,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : CellConversationRepository {

    override suspend fun getCellName(conversationId: QualifiedIDEntity): Either<StorageFailure, String?> =
        withContext(dispatchers.io) {
            wrapStorageRequest {
                conversation.getCellName(conversationId)
            }
        }

    override suspend fun setWireCell(conversationId: ConversationId, cellName: String?): Either<StorageFailure, Unit> =
        withContext(dispatchers.io) {
            wrapStorageRequest {
                conversation.setWireCell(QualifiedIDEntity(conversationId.value, conversationId.domain), cellName)
            }
        }
}
