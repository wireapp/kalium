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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.ShouldRemoteMuteChecker
import com.wire.kalium.logic.feature.call.ShouldRemoteMuteCheckerImpl
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

internal interface CallingMessageHandler {
    suspend fun handle(message: Message.Signaling, content: MessageContent.Calling)
}

@Suppress("LongParameterList")
internal class CallingMessageHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val callManager: Lazy<CallManager>,
    private val conversationRepository: ConversationRepository,
    private val muteCall: MuteCallUseCase,
    private val shouldRemoteMuteChecker: ShouldRemoteMuteChecker = ShouldRemoteMuteCheckerImpl(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CallingMessageHandler {
    private val tagWithUserId = "$TAG(${selfUserId.toLogString()})"

    override suspend fun handle(message: Message.Signaling, content: MessageContent.Calling) {
        val targetConversationId = if (message.isSelfMessage) {
            content.conversationId ?: message.conversationId
        } else {
            message.conversationId
        }
        val callingValue = json.decodeFromString<MessageContent.Calling.CallingValue>(content.value)
        when (callingValue.type) {
            REMOTE_MUTE_TYPE -> handleRemoteMuteMessage(
                message = message,
                targetConversationId = targetConversationId,
                callingValue = callingValue
            )

            else -> callManager.value.onCallingMessageReceived(message = message, content = content)
        }
    }

    private suspend fun handleRemoteMuteMessage(
        message: Message.Signaling,
        targetConversationId: ConversationId,
        callingValue: MessageContent.Calling.CallingValue
    ) {
        val clientId = currentClientIdProvider().getOrNull()
        if (clientId == null) {
            callingLogger.e("$tagWithUserId: Current client ID is not available. Cannot process calling $REMOTE_MUTE_TYPE message.")
            return
        }

        val conversationMembers = conversationRepository.observeConversationMembers(targetConversationId).first()
        val shouldRemoteMute = shouldRemoteMuteChecker.check(
            senderUserId = message.senderUserId,
            selfUserId = selfUserId,
            selfClientId = clientId.value,
            targets = callingValue.targets,
            conversationMembers = conversationMembers
        )
        callingLogger.i("$tagWithUserId: Calling $REMOTE_MUTE_TYPE message received for conversationId: $targetConversationId.")
        if (shouldRemoteMute) {
            muteCall(targetConversationId, true)
        }
    }

    internal companion object {
        internal const val TAG = "CallingMessageHandler"
        internal const val REMOTE_MUTE_TYPE = "REMOTEMUTE"
    }
}
