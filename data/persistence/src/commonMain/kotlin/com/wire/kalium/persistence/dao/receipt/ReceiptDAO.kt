/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.receipt

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.Receipt
import com.wire.kalium.persistence.ReceiptsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

interface ReceiptDAO {
    suspend fun insertReceipts(
        userId: UserIDEntity,
        conversationId: ConversationIDEntity,
        date: Instant,
        type: ReceiptTypeEntity,
        messageIds: List<String>
    )

    suspend fun observeDetailedReceiptsForMessage(
        conversationId: ConversationIDEntity,
        messageId: String,
        type: ReceiptTypeEntity
    ): Flow<List<DetailedReceiptEntity>>
}

class ReceiptDAOImpl(
    private val receiptsQueries: ReceiptsQueries,
    private val receiptAdapter: Receipt.Adapter,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : ReceiptDAO {

    override suspend fun insertReceipts(
        userId: UserIDEntity,
        conversationId: ConversationIDEntity,
        date: Instant,
        type: ReceiptTypeEntity,
        messageIds: List<String>
    ) = withContext(writeDispatcher.value) {
        receiptsQueries.transaction {
            messageIds.forEach { messageId ->
                receiptsQueries.insertReceipt(
                    messageId,
                    receiptAdapter.conversation_idAdapter.encode(conversationId),
                    receiptAdapter.user_idAdapter.encode(userId),
                    receiptAdapter.typeAdapter.encode(type),
                    date.toString()
                )
            }
        }
    }

    override suspend fun observeDetailedReceiptsForMessage(
        conversationId: ConversationIDEntity,
        messageId: String,
        type: ReceiptTypeEntity
    ): Flow<List<DetailedReceiptEntity>> =
        receiptsQueries.selectReceiptsByConversationIdAndMessageId(
            messageId = messageId,
            conversationId = conversationId,
            type = type,
            mapper = ReceiptMapper::fromDetailedReceiptView
        ).asFlow()
            .mapToList()
            .flowOn(readDispatcher.value)

}
