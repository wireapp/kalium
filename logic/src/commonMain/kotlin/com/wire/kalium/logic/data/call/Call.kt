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

package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId

enum class CallStatus {
    STARTED,
    INCOMING,
    MISSED,
    ANSWERED,
    ESTABLISHED,
    STILL_ONGOING,
    CLOSED_INTERNALLY, // Call terminated on current device only
    CLOSED, // call terminated everywhere
    REJECTED
}

data class Call(
    val conversationId: ConversationId,
    val status: CallStatus,
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val isCbrEnabled: Boolean,
    val callerId: String,
    val conversationName: String?,
    val conversationType: Conversation.Type,
    val callerName: String?,
    val callerTeamName: String?,
    val establishedTime: String? = null,
    val participants: List<Participant> = emptyList(),
    val maxParticipants: Int = 0 // Was used for tracking
)
