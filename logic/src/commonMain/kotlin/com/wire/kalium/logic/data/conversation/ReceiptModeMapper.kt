/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.persistence.dao.ConversationEntity

interface ReceiptModeMapper {
    fun toDaoModel(receiptMode: Conversation.ReceiptMode?): ConversationEntity.ReceiptMode
    fun fromApiToModel(receiptMode: ReceiptMode?): Conversation.ReceiptMode
    fun fromApiToDaoModel(receiptMode: ReceiptMode?): ConversationEntity.ReceiptMode
    fun fromEntityToModel(receiptMode: ConversationEntity.ReceiptMode?): Conversation.ReceiptMode
}

class ReceiptModeMapperImpl(
    val idMapper: IdMapper = MapperProvider.idMapper()
) : ReceiptModeMapper {
    override fun fromApiToDaoModel(receiptMode: ReceiptMode?): ConversationEntity.ReceiptMode = when (receiptMode) {
        ReceiptMode.DISABLED -> ConversationEntity.ReceiptMode.DISABLED
        ReceiptMode.ENABLED -> ConversationEntity.ReceiptMode.ENABLED
        null -> ConversationEntity.ReceiptMode.DISABLED
    }

    override fun toDaoModel(receiptMode: Conversation.ReceiptMode?): ConversationEntity.ReceiptMode = when (receiptMode) {
        Conversation.ReceiptMode.DISABLED -> ConversationEntity.ReceiptMode.DISABLED
        Conversation.ReceiptMode.ENABLED -> ConversationEntity.ReceiptMode.ENABLED
        null -> ConversationEntity.ReceiptMode.DISABLED
    }

    override fun fromApiToModel(receiptMode: ReceiptMode?): Conversation.ReceiptMode = when (receiptMode) {
        ReceiptMode.DISABLED -> Conversation.ReceiptMode.DISABLED
        ReceiptMode.ENABLED -> Conversation.ReceiptMode.ENABLED
        null -> Conversation.ReceiptMode.DISABLED
    }

    override fun fromEntityToModel(receiptMode: ConversationEntity.ReceiptMode?): Conversation.ReceiptMode = when (receiptMode) {
        ConversationEntity.ReceiptMode.DISABLED -> Conversation.ReceiptMode.DISABLED
        ConversationEntity.ReceiptMode.ENABLED -> Conversation.ReceiptMode.ENABLED
        null -> Conversation.ReceiptMode.DISABLED
    }
}
