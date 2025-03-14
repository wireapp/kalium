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

data class RecentlyEndedCallMetadata(
    val callEndReason: Int,
    val callDetails: CallDetails,
    val conversationDetails: ConversationDetails,
    val isTeamMember: Boolean
) {
    data class CallDetails(
        val isCallScreenShare: Boolean,
        val screenShareDurationInSeconds: Long,
        val callScreenShareUniques: Int,
        val isOutgoingCall: Boolean,
        val callDurationInSeconds: Long,
        val callParticipantsCount: Int,
        val conversationServices: Int,
        val callAVSwitchToggle: Boolean,
        val callVideoEnabled: Boolean
    )

    data class ConversationDetails(
        val conversationType: Conversation.Type,
        val conversationSize: Int,
        val conversationGuests: Int,
        val conversationGuestsPro: Int
    )
}
