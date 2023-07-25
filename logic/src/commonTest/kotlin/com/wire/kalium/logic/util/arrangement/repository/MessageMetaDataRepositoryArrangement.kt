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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

interface MessageMetaDataRepositoryArrangement {
    @Mock
    val messageMetadataRepository: MessageMetadataRepository

    fun withMessageOriginalSender(
        result: Either<StorageFailure, UserId>,
        conversationId: Matcher<ConversationId> = any(),
        messageId: Matcher<MessageId> = any()
    )
}

class MessageMetaDataRepositoryArrangementImpl : MessageMetaDataRepositoryArrangement {
    @Mock
    override val messageMetadataRepository: MessageMetadataRepository = mock(MessageMetadataRepository::class)

    override fun withMessageOriginalSender(
        result: Either<StorageFailure, UserId>,
        conversationId: Matcher<ConversationId>,
        messageId: Matcher<MessageId>
    ) {
        given(messageMetadataRepository)
            .suspendFunction(messageMetadataRepository::originalSenderId)
            .whenInvokedWith(conversationId, messageId)
            .thenReturn(result)
    }

}
