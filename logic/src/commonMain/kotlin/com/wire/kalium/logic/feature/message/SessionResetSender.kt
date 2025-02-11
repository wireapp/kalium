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

package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

interface SessionResetSender {
    suspend operator fun invoke(conversationId: ConversationId, userId: UserId, clientId: ClientId): Either<CoreFailure, Unit>
}

class SessionResetSenderImpl internal constructor(
    private val slowSyncRepository: SlowSyncRepository,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val messageSender: MessageSender,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : SessionResetSender {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
    ): Either<CoreFailure, Unit> = withContext(dispatchers.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
        val generatedMessageUuid = uuid4().toString()

        provideClientId().flatMap { selfClientId ->
            val message = Message.Signaling(
                id = generatedMessageUuid,
                content = MessageContent.ClientAction,
                conversationId = conversationId,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                senderClientId = selfClientId,
                status = Message.Status.Sent,
                isSelfMessage = true,
                expirationData = null
            )
            val recipient = Recipient(userId, listOf(clientId))
            messageSender.sendMessage(
                message,
                MessageTarget.Client(listOf(recipient))
            )
        }
    }

}
