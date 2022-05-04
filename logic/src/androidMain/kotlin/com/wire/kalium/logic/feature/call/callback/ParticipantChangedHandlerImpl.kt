package com.wire.kalium.logic.feature.call.callback

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.logic.data.call.AvsClient
import com.wire.kalium.logic.data.call.AvsClientList
import com.wire.kalium.logic.data.call.AvsParticipants
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.Participant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ParticipantChangedHandlerImpl(
    private val participantMapper: CallMapper.ParticipantMapper,
    private val onParticipantsChanged: (conversationId: String, participants: List<Participant>, clients: AvsClientList) -> Unit
) : ParticipantChangedHandler {

    override fun onParticipantChanged(conversationId: String, data: String, arg: Pointer?) {
        val participants = mutableListOf<Participant>()
        val clients = mutableListOf<AvsClient>()

        val participantsChange = Json.decodeFromString<AvsParticipants>(data)
        for (member in participantsChange.members) {
            participants.add(participantMapper.fromAVSMemberToParticipant(member = member))
            clients.add(participantMapper.fromAVSMemberToAvsClient(member = member))
        }

        onParticipantsChanged(
            conversationId,
            participants,
            AvsClientList(clients = clients)
        )
    }
}
