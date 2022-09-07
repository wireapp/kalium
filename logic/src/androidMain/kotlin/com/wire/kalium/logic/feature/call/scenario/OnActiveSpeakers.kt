package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.ActiveSpeakersHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper

class OnActiveSpeakers(
    private val callRepository: CallRepository,
    private val qualifiedIdMapper: QualifiedIdMapper
) : ActiveSpeakersHandler {

    override fun onActiveSpeakersChanged(inst: Handle, conversationId: String, data: String, arg: Pointer?) {
//         val callActiveSpeakers = Json.decodeFromString<CallActiveSpeakers>(data)
//         val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
//
//         callRepository.updateParticipantsActiveSpeaker(
//             conversationId = conversationIdWithDomain.toString(),
//             activeSpeakers = callActiveSpeakers
//         )
    }
}
