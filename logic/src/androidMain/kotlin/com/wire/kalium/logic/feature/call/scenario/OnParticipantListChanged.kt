package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallParticipants
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.Participant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OnParticipantListChanged(
    private val handle: Handle,
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val participantMapper: CallMapper.ParticipantMapper
) : ParticipantChangedHandler {

    override fun onParticipantChanged(conversationId: String, data: String, arg: Pointer?) {
        val participants = mutableListOf<Participant>()
        val clients = mutableListOf<CallClient>()

        val participantsChange = Json.decodeFromString<CallParticipants>(data)
        for (member in participantsChange.members) {
            participants.add(participantMapper.fromCallMemberToParticipant(member = member))
            clients.add(participantMapper.fromCallMemberToCallClient(member = member))
        }

        callRepository.updateCallParticipants(
            conversationId = conversationId,
            participants = participants
        )

        calling.wcall_request_video_streams(
            inst = handle,
            convId = conversationId,
            mode = DEFAULT_REQUEST_VIDEO_STREAMS_MODE,
            json = CallClientList(clients = clients).toJsonString()
        )

        callingLogger.i("onParticipantsChanged() - Total Participants: ${participants.size} for $conversationId")
    }

    private companion object {
        private const val DEFAULT_REQUEST_VIDEO_STREAMS_MODE = 0
    }
}
