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
<<<<<<< HEAD
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
=======
>>>>>>> 8728f137e5 (chore: Remove call to deleteSubConversation after ending 1:1 call (WPB-11007) (#3000))

/**
 * Helper class to handle call related operations.
 */
interface CallHelper {

    /**
     * Check if the OneOnOne call that uses SFT should be ended.
     * For Proteus, the call should be ended if the call has one participant after having 2 in the call.
     * For MLS, the call should be ended if the call has two participants and the second participant has lost audio.
     *
     * @param conversationId the conversation id.
     * @param callProtocol the call protocol.
     * @param conversationType the conversation type.
     * @param newCallParticipants the new call participants.
     * @param previousCallParticipants the previous call participants.
     * @return true if the call should be ended, false otherwise.
     */
    fun shouldEndSFTOneOnOneCall(
        conversationId: ConversationId,
        callProtocol: Conversation.ProtocolInfo?,
        conversationType: Conversation.Type,
        newCallParticipants: List<Participant>,
        previousCallParticipants: List<Participant>
    ): Boolean
}

class CallHelperImpl : CallHelper {

    override fun shouldEndSFTOneOnOneCall(
        conversationId: ConversationId,
        callProtocol: Conversation.ProtocolInfo?,
        conversationType: Conversation.Type,
        newCallParticipants: List<Participant>,
        previousCallParticipants: List<Participant>
    ): Boolean {
        return if (callProtocol is Conversation.ProtocolInfo.Proteus) {
            conversationType == Conversation.Type.ONE_ON_ONE &&
                    newCallParticipants.size == ONE_PARTICIPANTS &&
                    previousCallParticipants.size == TWO_PARTICIPANTS
        } else {
            conversationType == Conversation.Type.ONE_ON_ONE &&
                    newCallParticipants.size == TWO_PARTICIPANTS &&
                    previousCallParticipants.size == TWO_PARTICIPANTS &&
                    previousCallParticipants[1].hasEstablishedAudio && !newCallParticipants[1].hasEstablishedAudio
        }
    }

    companion object {
        const val TWO_PARTICIPANTS = 2
        const val ONE_PARTICIPANTS = 1
    }
}
