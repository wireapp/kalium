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
package com.wire.kalium.logic.feature.message.composite

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.datetime.Clock

/**
 * Use case for sending a button action message.
 * @param conversationId The conversation id.
 * @param messageId The id of the message that contains the button.
 * @param buttonId The id of the button.
 *
 * the action message is sent only to the message original sender.
 */
class SendButtonActionConfirmationMessageUseCase internal constructor(
    private val messageSender: MessageSender,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        buttonId: String,
        userIds: List<UserId>
    ): Result = syncManager.waitUntilLiveOrFailure().flatMap {
            currentClientIdProvider().flatMap { currentClientId ->
                val regularMessage = Message.Signaling(
                    id = uuid4().toString(),
                    content = MessageContent.ButtonActionConfirmation(
                        referencedMessageId = messageId,
                        buttonId = buttonId
                    ),
                    conversationId = conversationId,
                    date = Clock.System.now(),
                    senderUserId = selfUserId,
                    senderClientId = currentClientId,
                    status = Message.Status.Pending,
                    isSelfMessage = true,
                    expirationData = null
                )
                messageSender.sendMessage(regularMessage, messageTarget = MessageTarget.Users(userIds))
            }
    }.fold(Result::Failure, { Result.Success })

    sealed interface Result {
        data object Success : Result
        data class Failure(
            val error: CoreFailure
        ) : Result
    }
}
