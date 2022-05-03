package com.wire.kalium.logic.feature.call.callback

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.logic.data.call.AvsClient
import com.wire.kalium.logic.data.call.AvsClientList
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.asString
import com.wire.kalium.logic.util.getDomain
import com.wire.kalium.logic.util.removeDomain
import org.json.JSONObject

class ParticipantChangedHandlerImpl(
    private val onParticipantsChanged: (conversationId: String, participants: List<Participant>, clients: AvsClientList) -> Unit
) : ParticipantChangedHandler {

    override fun onParticipantChanged(conversationId: String, data: String, arg: Pointer?) {
        val participantsChange = JSONObject(data)
        val members = participantsChange.getJSONArray("members")

        val participants = mutableListOf<Participant>()
        val clients = mutableListOf<AvsClient>()
        for (i in 0 until members.length()) {
            val member = members.getJSONObject(i)

            val userId = member.getString("userid").removeDomain()
            val userDomain = member.getString("userid").getDomain()
            val clientId = member.getString("clientid")
            val qualifiedID = QualifiedID(
                value = userId,
                domain = userDomain
            )

            participants.add(
                Participant(
                    id = qualifiedID,
                    clientId = clientId,
                    muted = member.getInt("muted") == 1
                )
            )
            clients.add(
                AvsClient(
                    userId = qualifiedID.asString(),
                    clientId = clientId
                )
            )
        }

        onParticipantsChanged(
            conversationId,
            participants,
            AvsClientList(clients = clients)
        ) // TODO: workout the deep implementations
    }
}
