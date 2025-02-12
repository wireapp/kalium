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
package com.wire.kalium.logic.data.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal interface SystemMessageInserter {
    suspend fun insertProtocolChangedSystemMessage(
        conversationId: ConversationId,
        senderUserId: UserId,
        protocol: Conversation.Protocol
    )

    suspend fun insertProtocolChangedDuringACallSystemMessage(
        conversationId: ConversationId,
        senderUserId: UserId
    )

    suspend fun insertHistoryLostProtocolChangedSystemMessage(
        conversationId: ConversationId
    )

    suspend fun insertLostCommitSystemMessage(conversationId: ConversationId, instant: Instant): Either<CoreFailure, Unit>
}

internal class SystemMessageInserterImpl(
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase
) : SystemMessageInserter {
    override suspend fun insertProtocolChangedSystemMessage(
        conversationId: ConversationId,
        senderUserId: UserId,
        protocol: Conversation.Protocol
    ) {
        val message = Message.System(
            uuid4().toString(),
            MessageContent.ConversationProtocolChanged(
                protocol = protocol
            ),
            conversationId,
            Clock.System.now(),
            senderUserId,
            Message.Status.Sent,
            Message.Visibility.VISIBLE,
            null
        )

        persistMessage(message)
    }

    override suspend fun insertProtocolChangedDuringACallSystemMessage(
        conversationId: ConversationId,
        senderUserId: UserId
    ) {
        val message = Message.System(
            uuid4().toString(),
            MessageContent.ConversationProtocolChangedDuringACall,
            conversationId,
            Clock.System.now(),
            senderUserId,
            Message.Status.Sent,
            Message.Visibility.VISIBLE,
            null
        )

        persistMessage(message)
    }

    override suspend fun insertHistoryLostProtocolChangedSystemMessage(conversationId: ConversationId) {
        val message = Message.System(
            uuid4().toString(),
            MessageContent.HistoryLostProtocolChanged,
            conversationId,
            Clock.System.now(),
            selfUserId,
            Message.Status.Sent,
            Message.Visibility.VISIBLE,
            null
        )

        persistMessage(message)
    }

    override suspend fun insertLostCommitSystemMessage(conversationId: ConversationId, instant: Instant): Either<CoreFailure, Unit> {
        val mlsEpochWarningMessage = Message.System(
            id = uuid4().toString(),
            content = MessageContent.MLSWrongEpochWarning,
            conversationId = conversationId,
            date = instant,
            senderUserId = selfUserId,
            status = Message.Status.Read(0),
            visibility = Message.Visibility.VISIBLE,
            senderUserName = null,
            expirationData = null
        )
        return persistMessage(mlsEpochWarningMessage)
    }
}
