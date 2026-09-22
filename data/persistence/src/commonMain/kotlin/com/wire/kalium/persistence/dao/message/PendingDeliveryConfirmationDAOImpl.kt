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
package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.wire.kalium.persistence.PendingDeliveryConfirmationsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

internal class PendingDeliveryConfirmationDAOImpl(
    private val queries: PendingDeliveryConfirmationsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : PendingDeliveryConfirmationDAO {
    override suspend fun enqueue(conversationId: ConversationIDEntity, messageId: String) {
        withContext(writeDispatcher.value) { queries.insertOrIgnore(conversationId, messageId) }
    }

    override suspend fun getPendingAfter(id: Long, limit: Long): List<PendingDeliveryConfirmationEntity> =
        withContext(readDispatcher.value) {
            queries.selectPendingAfter(id, limit, ::PendingDeliveryConfirmationEntity).awaitAsList()
        }

    override suspend fun acknowledge(ids: List<Long>) {
        withContext(writeDispatcher.value) { queries.deleteByIds(ids) }
    }
}
