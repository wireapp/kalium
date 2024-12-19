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
package com.wire.kalium.logic.feature.incallreaction

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.datetime.Clock

/**
 * Sends in-call reaction to the call with the conversationId
 */
class SendInCallReactionUseCase(
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val messageSender: MessageSender,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val scope: CoroutineScope
) {

    suspend operator fun invoke(conversationId: ConversationId, reaction: String): Either<CoreFailure, Unit> =
        scope.async(dispatchers.io) {

            val generatedMessageUuid = uuid4().toString()

            provideClientId().flatMap { clientId ->
                val message = Message.Signaling(
                    id = generatedMessageUuid,
                    content = MessageContent.InCallEmoji(
                        emojis = mapOf(reaction to 1)
                    ),
                    conversationId = conversationId,
                    date = Clock.System.now(),
                    senderUserId = selfUserId,
                    senderClientId = clientId,
                    status = Message.Status.Pending,
                    isSelfMessage = true,
                    expirationData = null,
                )

                messageSender.sendMessage(message)
            }
        }.await()
}
