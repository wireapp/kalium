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

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.Mockable
import kotlinx.coroutines.flow.firstOrNull

/**
 * Helper class to handle call related operations.
 */
@Mockable
internal interface CallHelper {

    /**
     * Check if the OneOnOne call that uses SFT should be ended when the participants of that call are changed.
     * The call should be ended in that case when:
     * - the config states that SFT should be used for 1on1 calls
     * - the call for given conversationId is established
     * - the conversation is 1on1
     * - the participants of the call are changed from 2 to 1, for both Proteus and MLS
     *
     * @param conversationId the conversation id.
     * @param newCallParticipants the new call participants.
     * @return true if the call should be ended, false otherwise.
     */
    suspend fun shouldEndSFTOneOnOneCall(
        conversationId: ConversationId,
        newCallParticipants: List<ParticipantMinimized>,
    ): Boolean
}

internal class CallHelperImpl(
    private val userConfigRepository: UserConfigRepository,
    private val callRepository: CallRepository,
) : CallHelper {

    override suspend fun shouldEndSFTOneOnOneCall(
        conversationId: ConversationId,
        newCallParticipants: List<ParticipantMinimized>,
    ): Boolean =
        userConfigRepository.shouldUseSFTForOneOnOneCalls().getOrElse(false).takeIf { it }?.let {
            callRepository.establishedCallsFlow().firstOrNull()?.firstOrNull { it.conversationId == conversationId }?.let { call ->
                call.conversationType == Conversation.Type.OneOnOne &&
                        call.participants.size == TWO_PARTICIPANTS &&
                        newCallParticipants.size == ONE_PARTICIPANTS
            }
        } ?: false

    internal companion object {
        internal const val TWO_PARTICIPANTS = 2
        internal const val ONE_PARTICIPANTS = 1
    }
}
