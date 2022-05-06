package com.wire.kalium.logic.feature.call.callback

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallParticipants
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.Participant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ParticipantChangedHandlerImpl(
    private val participantMapper: CallMapper.ParticipantMapper,
    private val onParticipantsChanged: (conversationId: String, participants: List<Participant>, clients: CallClientList) -> Unit
) : ParticipantChangedHandler {

    override fun onParticipantChanged(conversationId: String, data: String, arg: Pointer?) {
        val participants = mutableListOf<Participant>()
        val clients = mutableListOf<CallClient>()

        val participantsChange = Json.decodeFromString<CallParticipants>(data)
        for (member in participantsChange.members) {
            participants.add(participantMapper.fromCallMemberToParticipant(member = member))
            clients.add(participantMapper.fromCallMemberToCallClient(member = member))
        }

        onParticipantsChanged(
            conversationId,
            participants,
            CallClientList(clients = clients)
        )
    }
}
