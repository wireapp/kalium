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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.appsAccessChangedMessage
import com.wire.kalium.logic.data.conversation.mlsEpochWarningMessage
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.UserSessionBinding
import com.wire.kalium.persistence.dao.message.LocalId
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

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

    suspend fun insertConversationStartedUnverifiedWarning(conversationId: ConversationId)

    suspend fun insertConversationAppsAccessChanged(
        eventId: String = LocalId.generate(),
        conversationId: ConversationId,
        senderUserId: UserId,
        isAppsAccessEnabled: Boolean
    )
}

@Inject
internal class SystemMessageInserterImpl(
    @UserSessionBinding("userId")
    private val selfUserId: UserId,
    @UserSessionBinding("persistMessage")
    private val persistMessage: PersistMessageUseCase
) : SystemMessageInserter {
    override suspend fun insertProtocolChangedSystemMessage(
        conversationId: ConversationId,
        senderUserId: UserId,
        protocol: Conversation.Protocol
    ) {
        val message = Message.System(
            Uuid.random().toString(),
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
            Uuid.random().toString(),
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
            Uuid.random().toString(),
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
        val mlsEpochWarningMessage = mlsEpochWarningMessage(Uuid.random().toString(), conversationId, selfUserId, instant)
        return persistMessage(mlsEpochWarningMessage)
    }

    override suspend fun insertConversationStartedUnverifiedWarning(conversationId: ConversationId) {
        persistMessage(
            Message.System(
                id = LocalId.generate(),
                content = MessageContent.ConversationStartedUnverifiedWarning,
                conversationId = conversationId,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                status = Message.Status.Sent,
                visibility = Message.Visibility.VISIBLE,
                expirationData = null
            )
        )
    }

    override suspend fun insertConversationAppsAccessChanged(
        eventId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        isAppsAccessEnabled: Boolean
    ) {
        persistMessage(appsAccessChangedMessage(eventId, conversationId, senderUserId, isAppsAccessEnabled))
    }
}
