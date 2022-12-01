package com.wire.kalium.logic.framework.stub

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.receipt.DetailedReceipt
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

open class ReceiptRepositoryStub : ReceiptRepository {

    override suspend fun persistReceipts(
        userId: UserId,
        conversationId: ConversationId,
        date: Instant,
        type: ReceiptType,
        vararg messageIds: String
    ): Unit = Unit

    override suspend fun observeMessageReceipts(
        conversationId: ConversationId,
        messageId: String,
        type: ReceiptType
    ): Flow<List<DetailedReceipt>> {
        return flowOf(listOf())
    }
}
