package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.ActiveSpeakersHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.call.CallActiveSpeakers
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OnActiveSpeakers(
    private val callRepository: CallRepository,
    private val activeSpeakerMapper: CallMapper.ActiveSpeakerMapper
) : ActiveSpeakersHandler {

    override fun onActiveSpeakersChanged(inst: Handle, conversationId: String, data: String, arg: Pointer?) {
        val callActiveSpeakers = Json.decodeFromString<CallActiveSpeakers>(data)
        val activeSpeakers = callActiveSpeakers.activeSpeakers.map(activeSpeakerMapper::fromCallActiveSpeakerToActiveSpeaker)

        callRepository.updateActiveSpeakers(
            conversationId = conversationId,
            activeSpeakers = activeSpeakers
        )
    }
}
