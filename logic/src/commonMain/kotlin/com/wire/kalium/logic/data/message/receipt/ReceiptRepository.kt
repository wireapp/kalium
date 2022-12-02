package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

interface ReceiptRepository {
    suspend fun persistReceipts(
        userId: UserId,
        conversationId: ConversationId,
        date: Instant,
        type: ReceiptType,
        messageIds: List<String>
    )

    suspend fun observeMessageReceipts(
        conversationId: ConversationId,
        messageId: String,
        type: ReceiptType
    ): Flow<List<DetailedReceipt>>
}

class ReceiptRepositoryImpl(
    private val receiptDAO: ReceiptDAO,
    private val receiptsMapper: ReceiptsMapper = MapperProvider.receiptsMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : ReceiptRepository {

    override suspend fun persistReceipts(
        userId: UserId,
        conversationId: ConversationId,
        date: Instant,
        type: ReceiptType,
        messageIds: List<String>
    ) {
        receiptDAO.insertReceipts(
            userId = idMapper.toDaoModel(userId),
            conversationId = idMapper.toDaoModel(conversationId),
            date = date,
            type = receiptsMapper.toTypeEntity(type),
            messageIds = messageIds
        )
    }

    override suspend fun observeMessageReceipts(
        conversationId: ConversationId,
        messageId: String,
        type: ReceiptType
    ): Flow<List<DetailedReceipt>> =
        receiptDAO.observeDetailedReceiptsForMessage(
            conversationId = idMapper.toDaoModel(conversationId),
            messageId = messageId,
            type = receiptsMapper.toTypeEntity(type = type)
        ).map {
            it.map { detailedReceipt ->
                receiptsMapper.fromEntityToModel(
                    detailedReceiptEntity = detailedReceipt
                )
            }
        }
}
