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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.id.ConversationId

internal fun ConversationDetailsWithEvents.withOngoingCall(
    ongoingCallConversationIds: Set<ConversationId>
): ConversationDetailsWithEvents {
    val hasOngoingCall = conversationDetails.isOngoingGroupCall(ongoingCallConversationIds)
    return copy(
        hasOngoingCall = hasOngoingCall,
        hasNewActivitiesToShow = hasNewActivitiesToShow || hasOngoingCall
    )
}

internal fun List<ConversationDetailsWithEvents>.withOngoingCalls(
    ongoingCallConversationIds: Set<ConversationId>,
    moveOngoingCallsOnTop: Boolean = true
): List<ConversationDetailsWithEvents> {
    val conversationsWithOngoingCalls = map { conversation ->
        conversation.withOngoingCall(ongoingCallConversationIds)
    }
    return if (moveOngoingCallsOnTop) {
        conversationsWithOngoingCalls.sortedByDescending {
            it.conversationDetails.isOngoingGroupCall(ongoingCallConversationIds)
        }
    } else {
        conversationsWithOngoingCalls
    }
}

private fun ConversationDetails.isOngoingGroupCall(ongoingCallConversationIds: Set<ConversationId>): Boolean =
    conversation.id in ongoingCallConversationIds && this is ConversationDetails.Group
