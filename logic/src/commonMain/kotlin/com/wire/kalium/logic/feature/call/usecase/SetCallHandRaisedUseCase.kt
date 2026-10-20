/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.call.CallHandIdentity
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.messaging.sending.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Sends immediately; failed signals are retried only by an explicit new user action. */
public class SetCallHandRaisedUseCase internal constructor(
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val messageSender: MessageSender,
    private val calls: CallRepository,
    private val scope: CoroutineScope,
) {
    private val pending = Mutex()

    public suspend operator fun invoke(conversationId: ConversationId, isRaised: Boolean, generation: String): Result = scope.async {
        if (!pending.tryLock()) return@async Result.Busy
        try {
            send(conversationId, isRaised, generation)
        } finally {
            pending.unlock()
        }
    }.await()

    private suspend fun send(
        conversationId: ConversationId,
        isRaised: Boolean,
        generation: String
    ): Result = when (val client = provideClientId()) {
        is Either.Left -> Result.Failure(client.value)
        is Either.Right -> sendForClient(conversationId, isRaised, client.value, generation)
    }

    private suspend fun sendForClient(
        conversationId: ConversationId,
        isRaised: Boolean,
        client: ClientId,
        generation: String
    ): Result {
        val metadata = calls.getCallMetadata(conversationId)
        val hands = metadata?.handState
        val identity = CallHandIdentity(selfUserId, client.value)
        return when {
            hands == null || metadata.callStatus != CallStatus.ESTABLISHED || !hands.contains(identity) -> Result.NotJoined
            hands.generation != generation -> Result.NotJoined
            (identity in hands.raised) == isRaised -> Result.Success
            else -> sendSignal(conversationId, isRaised, client, identity, hands.generation)
        }
    }

    private suspend fun sendSignal(
        conversationId: ConversationId,
        isRaised: Boolean,
        client: ClientId,
        identity: CallHandIdentity,
        generation: String
    ): Result {
        val message = Message.Signaling(
            id = Uuid.random().toString(), content = MessageContent.InCallHandRaise(isRaised),
            conversationId = conversationId, date = Clock.System.now(), senderUserId = selfUserId,
            senderClientId = client, status = Message.Status.Pending, isSelfMessage = true, expirationData = null,
        )
        return when (val sent = messageSender.sendMessage(message)) {
            is Either.Left -> Result.Failure(sent.value)
            is Either.Right -> if (calls.updateHand(conversationId, identity, isRaised, message.date, message.id, generation)) {
                Result.Success
            } else {
                Result.NotJoined
            }
        }
    }

    public sealed interface Result {
        public data object Success : Result
        public data object Busy : Result
        public data object NotJoined : Result
        public data class Failure(public val error: CoreFailure) : Result
    }
}
