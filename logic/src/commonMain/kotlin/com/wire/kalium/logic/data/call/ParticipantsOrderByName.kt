package com.wire.kalium.logic.data.call

interface ParticipantsOrderByName {
    fun sortItems(participants: List<Participant>): List<Participant>
}

class ParticipantsOrderByNameImpl : ParticipantsOrderByName {
    override fun sortItems(participants: List<Participant>) = participants.sortedBy { it.name.uppercase() }
}
