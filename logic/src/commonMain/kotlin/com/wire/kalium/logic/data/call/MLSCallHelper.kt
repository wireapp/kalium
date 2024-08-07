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

import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest

/**
 * Helper class to handle MLS call related operations.
 */
interface MLSCallHelper {

    /**
     * Check if the OneOnOne MLS call that uses SFT should be ended.
     * The call should be ended if the call has two participants and the second participant has lost audio.
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

    /**
     * Handle the call termination.
     * If the call is a one on one call is oneOneOne on SFT, then delete MLS sub conversation
     * otherwise leave the MLS conference.
     *
     * @param conversationId the conversation id.
     * @param callProtocol the call protocol.
     * @param conversationType the conversation type.
     */
    suspend fun handleCallTermination(
        conversationId: ConversationId,
        conversationType: Conversation.Type?
    )
}

class MLSCallHelperImpl(
    private val callRepository: CallRepository,
    private val subconversationRepository: SubconversationRepository,
    private val userConfigRepository: UserConfigRepository
) : MLSCallHelper {

    override fun shouldEndSFTOneOnOneCall(
        conversationId: ConversationId,
        callProtocol: Conversation.ProtocolInfo?,
        conversationType: Conversation.Type,
        newCallParticipants: List<Participant>,
        previousCallParticipants: List<Participant>
    ) = callProtocol is Conversation.ProtocolInfo.MLS &&
            userConfigRepository.shouldUseSFTForOneOnOneCalls().getOrElse(false) &&
            conversationType == Conversation.Type.ONE_ON_ONE &&
            newCallParticipants.size == TWO_PARTICIPANTS &&
            previousCallParticipants.size == TWO_PARTICIPANTS &&
            previousCallParticipants[1].hasEstablishedAudio && !newCallParticipants[1].hasEstablishedAudio

    override suspend fun handleCallTermination(
        conversationId: ConversationId,
        conversationType: Conversation.Type?
    ) {
        if (userConfigRepository.shouldUseSFTForOneOnOneCalls().getOrElse(false) &&
            conversationType == Conversation.Type.ONE_ON_ONE
        ) {
            callingLogger.i("[MLSCallHelper] -> fetching remote MLS sub conversation details")
            subconversationRepository.fetchRemoteSubConversationDetails(
                conversationId,
                CALL_SUBCONVERSATION_ID
            ).onSuccess { subconversationDetails ->
                callingLogger.i("[MLSCallHelper] -> Deleting remote MLS sub conversation")
                subconversationRepository.deleteRemoteSubConversation(
                    subconversationDetails.parentId.toModel(),
                    SubconversationId(subconversationDetails.id),
                    SubconversationDeleteRequest(
                        subconversationDetails.epoch,
                        subconversationDetails.groupId
                    )
                )
            }.onFailure {
                callingLogger.e("[MLSCallHelper] -> Error fetching remote MLS sub conversation details")
            }
        } else {
            callingLogger.i("[MLSCallHelper] -> Leaving MLS conference")
            callRepository.leaveMlsConference(conversationId)
        }
    }

    companion object {
        const val TWO_PARTICIPANTS = 2
    }
}
