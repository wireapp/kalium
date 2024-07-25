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
package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.data.user.UserId

/**
 * Represents the instructions for sending a calling message.
 *
 * @property context The pointer context for the message (optional).
 * @property callHostConversationId The ID of the conversation where the call is taking place.
 * @property messageString The content of the message.
 * @property avsSelfUserId The self user ID used by AVS.
 * @property avsSelfClientId The self client ID used by AVS.
 * @property messageTarget The target for sending the message.
 */
data class CallingMessageInstructions(
    val context: Pointer?,
    val callHostConversationId: ConversationId,
    val messageString: String,
    val avsSelfUserId: UserId,
    val avsSelfClientId: ClientId,
    val messageTarget: CallingMessageTarget
)

sealed interface CallingMessageTarget {
    val specificTarget: MessageTarget

    /**
     * Send the message only to other devices of self-user.
     */
    data object Self : CallingMessageTarget {
        override val specificTarget: MessageTarget
            get() = MessageTarget.Conversation()
    }

    /**
     * Send the message to the host conversation.
     * Supports ignoring users through the [specificTarget].
     */
    data class HostConversation(
        override val specificTarget: MessageTarget = MessageTarget.Conversation()
    ) : CallingMessageTarget
}
