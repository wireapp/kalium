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
package com.wire.kalium.logic.data.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil

internal interface SystemMessageInserter {
    suspend fun insertProtocolChangedSystemMessage(
        conversationId: ConversationId,
        senderUserId: UserId,
        protocol: Conversation.Protocol
    )
    suspend fun insertHistoryLostProtocolChangedSystemMessage(
        conversationId: ConversationId
    )

    suspend fun insertLostCommitSystemMessage(conversationId: ConversationId, dateIso: String): Either<CoreFailure, Unit>
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
            DateTimeUtil.currentIsoDateTimeString(),
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
            DateTimeUtil.currentIsoDateTimeString(),
            selfUserId,
            Message.Status.Sent,
            Message.Visibility.VISIBLE,
            null
        )

        persistMessage(message)
    }

    override suspend fun insertLostCommitSystemMessage(conversationId: ConversationId, dateIso: String): Either<CoreFailure, Unit> {
        val mlsEpochWarningMessage = Message.System(
            id = uuid4().toString(),
            content = MessageContent.MLSWrongEpochWarning,
            conversationId = conversationId,
            date = dateIso,
            senderUserId = selfUserId,
            status = Message.Status.Read(0),
            visibility = Message.Visibility.VISIBLE,
            senderUserName = null,
            expirationData = null
        )
        return persistMessage(mlsEpochWarningMessage)
    }
}
