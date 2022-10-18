package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.user.UserId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantsOrderByNameTest {

    lateinit var participantsOrderByName: ParticipantsOrderByName

    @BeforeTest
    fun setUp() {
        participantsOrderByName = ParticipantsOrderByNameImpl()
    }

    @Test
    fun givenAListOfNonOrderedParticipants_whenOrderingParticipants_thenReturnAParticipantsListOrderedByName() {
        val participant1 = Participant(
            id = UserId("participant1", "domain"),
            clientId = "clientId",
            name = "Alok",
            isCameraOn = true,
            isMuted = false
        )
        val participant2 = Participant(
            id = UserId("participant2", "domain"),
            clientId = "clientId",
            name = "Max",
            isCameraOn = true,
            isMuted = false
        )
        val participant3 = Participant(
            id = UserId("participant3", "domain"),
            clientId = "clientId",
            name = "Hisoka",
            isCameraOn = true,
            isMuted = false
        )
        val participants = listOf(participant2, participant1, participant3)

        val result = participantsOrderByName.sortItems(participants)

        assertEquals(3, result.size)
        assertEquals(participant1, result.first())
        assertEquals(participant2, result.last())

    }
}
