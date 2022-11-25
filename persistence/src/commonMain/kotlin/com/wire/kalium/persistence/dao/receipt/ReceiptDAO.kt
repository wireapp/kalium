package com.wire.kalium.persistence.dao.receipt

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.wire.kalium.persistence.ReceiptsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

interface ReceiptDAO {
    suspend fun insertReceipts(
        userId: UserIDEntity,
        conversationId: ConversationIDEntity,
        date: Instant,
        type: ReceiptTypeEntity,
        vararg messageIds: String
    )

    suspend fun observeDetailedReceiptsForMessage(
        conversationId: ConversationIDEntity,
        messageId: String,
        type: ReceiptTypeEntity
    ): Flow<List<DetailedReceiptEntity>>
}

class ReceiptDAOImpl(
    private val receiptsQueries: ReceiptsQueries
) : ReceiptDAO {

    override suspend fun insertReceipts(
        userId: UserIDEntity,
        conversationId: ConversationIDEntity,
        date: Instant,
        type: ReceiptTypeEntity,
        vararg messageIds: String
    ) {
        receiptsQueries.transaction {
            messageIds.forEach { messageId ->
                receiptsQueries.insertReceipt(
                    messageId, conversationId, userId, type, date.toString()
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
        ).asFlow().map {
            it.executeAsList()
        }
}
