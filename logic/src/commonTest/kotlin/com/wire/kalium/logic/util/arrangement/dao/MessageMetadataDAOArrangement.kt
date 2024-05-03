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
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageMetadataDAO
import io.mockative.Matchers
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.matches
import io.mockative.mock

interface MessageMetadataDAOArrangement {
    @Mock
    val messageMetaDataDAO: MessageMetadataDAO

    suspend fun withMessageOriginalSender(
        result: UserIDEntity?,
        conversationId: (ConversationIDEntity) -> Boolean = { true },
        messageId: (String) -> Boolean = { true }
    )
}

class MessageMetadataDAOArrangementImpl : MessageMetadataDAOArrangement {
    @Mock
    override val messageMetaDataDAO: MessageMetadataDAO = mock(MessageMetadataDAO::class)

    override suspend fun withMessageOriginalSender(
        result: UserIDEntity?,
        conversationId: (ConversationIDEntity) -> Boolean,
        messageId: (String) -> Boolean
    ) {
        coEvery {
            messageMetaDataDAO.originalSenderId(
                matches { conversationId(it) },
                matches { messageId(it) }
            )
        }.returns(result)
    }
}
