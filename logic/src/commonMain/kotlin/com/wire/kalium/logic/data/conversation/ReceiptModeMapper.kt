package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.persistence.dao.ConversationEntity

interface ReceiptModeMapper {
    fun toDaoModel(receiptMode: Conversation.ReceiptMode): ConversationEntity.ReceiptMode
    fun fromApiToModel(receiptMode: ReceiptMode): Conversation.ReceiptMode
    fun fromApiToDaoModel(receiptMode: ReceiptMode): ConversationEntity.ReceiptMode
    fun fromEntityToModel(receiptMode: ConversationEntity.ReceiptMode): Conversation.ReceiptMode
    fun fromModelToApi(receiptMode: Conversation.ReceiptMode): ReceiptMode
}

class ReceiptModeMapperImpl(
    val idMapper: IdMapper = MapperProvider.idMapper()
) : ReceiptModeMapper {
    override fun fromApiToDaoModel(receiptMode: ReceiptMode): ConversationEntity.ReceiptMode = when (receiptMode) {
        ReceiptMode.DISABLED -> ConversationEntity.ReceiptMode.DISABLED
        ReceiptMode.ENABLED -> ConversationEntity.ReceiptMode.ENABLED
    }

    override fun toDaoModel(receiptMode: Conversation.ReceiptMode): ConversationEntity.ReceiptMode = when (receiptMode) {
        Conversation.ReceiptMode.DISABLED -> ConversationEntity.ReceiptMode.DISABLED
        Conversation.ReceiptMode.ENABLED -> ConversationEntity.ReceiptMode.ENABLED
    }

    override fun fromApiToModel(receiptMode: ReceiptMode): Conversation.ReceiptMode = when (receiptMode) {
        ReceiptMode.DISABLED -> Conversation.ReceiptMode.DISABLED
        ReceiptMode.ENABLED -> Conversation.ReceiptMode.ENABLED
    }

    override fun fromEntityToModel(receiptMode: ConversationEntity.ReceiptMode): Conversation.ReceiptMode = when (receiptMode) {
        ConversationEntity.ReceiptMode.DISABLED -> Conversation.ReceiptMode.DISABLED
        ConversationEntity.ReceiptMode.ENABLED -> Conversation.ReceiptMode.ENABLED
    }

    override fun fromModelToApi(receiptMode: Conversation.ReceiptMode): ReceiptMode = when (receiptMode) {
        Conversation.ReceiptMode.ENABLED -> ReceiptMode.ENABLED
        Conversation.ReceiptMode.DISABLED -> ReceiptMode.DISABLED
    }
}
