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
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallParticipants
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallHelper
import com.wire.kalium.logic.data.call.ParticipantMinimized
import com.wire.kalium.logic.data.call.mapper.ParticipantMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Suppress("LongParameterList")
class OnParticipantListChanged internal constructor(
    private val callRepository: CallRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val participantMapper: ParticipantMapper,
    private val userConfigRepository: UserConfigRepository,
    private val callHelper: CallHelper,
    private val endCall: suspend (conversationId: ConversationId) -> Unit,
    private val callingScope: CoroutineScope,
    private val jsonDecoder: Json = Json
) : ParticipantChangedHandler {

    override fun onParticipantChanged(remoteConversationId: String, data: String, arg: Pointer?) {

        val participantsChange = jsonDecoder.decodeFromString<CallParticipants>(data)

        callingScope.launch {
            val participants = mutableListOf<ParticipantMinimized>()
            val conversationIdWithDomain =
                qualifiedIdMapper.fromStringToQualifiedID(remoteConversationId)

            participantsChange.members.map { member ->
                participants.add(participantMapper.fromCallMemberToParticipantMinimized(member))
            }

            if (userConfigRepository.shouldUseSFTForOneOnOneCalls().getOrElse(false)) {
                val callProtocol = callRepository.currentCallProtocol(conversationIdWithDomain)

                val currentCall = callRepository.establishedCallsFlow().first().firstOrNull()
                currentCall?.let {
                    val shouldEndSFTOneOnOneCall = callHelper.shouldEndSFTOneOnOneCall(
                        conversationId = conversationIdWithDomain,
                        callProtocol = callProtocol,
                        conversationType = it.conversationType,
                        newCallParticipants = participants,
                        previousCallParticipants = it.participants
                    )
                    if (shouldEndSFTOneOnOneCall) {
                        kaliumLogger.i("[onParticipantChanged] - Ending SFT one on one call due to participant leaving")
                        endCall(conversationIdWithDomain)
                    }
                }
            }

            callRepository.updateCallParticipants(
                conversationId = conversationIdWithDomain,
                participants = participants
            )
            callingLogger.i(
                "[onParticipantsChanged] - Total Participants: ${participants.size}" +
                        " | ConversationId: ${remoteConversationId.obfuscateId()}"
            )
        }
    }
}
