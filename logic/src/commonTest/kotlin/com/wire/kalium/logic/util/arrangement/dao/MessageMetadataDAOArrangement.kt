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
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageMetadataDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

interface MessageMetadataDAOArrangement {
    @Mock
    val messageMetaDataDAO: MessageMetadataDAO

    fun withMessageOriginalSender(
        result: UserIDEntity?,
        conversationId: Matcher<ConversationIDEntity> = any(),
        messageId: Matcher<String> = any()
    )
}

class MessageMetadataDAOArrangementImpl : MessageMetadataDAOArrangement {
    @Mock
    override val messageMetaDataDAO: MessageMetadataDAO = mock(MessageMetadataDAO::class)

    override fun withMessageOriginalSender(
        result: UserIDEntity?,
        conversationId: Matcher<ConversationIDEntity>,
        messageId: Matcher<String>
    ) {
        given(messageMetaDataDAO)
            .suspendFunction(messageMetaDataDAO::originalSenderId)
            .whenInvokedWith(conversationId, messageId)
            .thenReturn(result)
    }
}
