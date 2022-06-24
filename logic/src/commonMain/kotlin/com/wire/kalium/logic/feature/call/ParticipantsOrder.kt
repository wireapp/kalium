package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.Participant

interface ParticipantsOrder {
    fun reorderItems(participants: List<Participant>): List<Participant>
}

class ParticipantsOrderImpl : ParticipantsOrder {
    /**
     * order alphabetically list of participants except the first one which is the self user
     */
    override fun reorderItems(participants: List<Participant>): List<Participant> {
        return if (participants.isNotEmpty()) {
            val subParticipants = participants.subList(1, participants.size)
            val sortedSubParticipants = subParticipants.sortedBy { it.name }
            listOf(participants.first()) + sortedSubParticipants
        } else participants
    }
}
