package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantsOrderTest {

    private lateinit var participantsOrder: ParticipantsOrder

    @BeforeTest
    fun setup() {
        participantsOrder = ParticipantsOrder()
    }

    @Test
    fun givenAnEmptyListOfParticipants_whenRunningUseCase_thenDoNotOrderItemsAndReturnEmptyList() = runTest {
        val emptyParticipantsList = listOf<Participant>()

        val result = participantsOrder.reorderItems(emptyParticipantsList)

        assertEquals(emptyParticipantsList, result)
    }

    @Test
    fun givenAListOfParticipants_whenRunningUseCase_thenOrderItemsAlphabeticallyByNameExceptFirstOne() = runTest {
        val participants = listOf(participant1, participant2, participant3)

        val result = participantsOrder.reorderItems(participants)

        assertEquals(participants.size, result.size)
        assertEquals(participants.first(), result.first())
        assertEquals(participants[1], result[2])
    }

    companion object {
        val participant1 = Participant(
            id = QualifiedID("", ""),
            clientId = "",
            isMuted = false,
            name = "self user"
        )
        val participant2 = Participant(
            id = QualifiedID("", ""),
            clientId = "",
            isMuted = false,
            name = "user name"
        )
        val participant3 = Participant(
            id = QualifiedID("", ""),
            clientId = "",
            isMuted = false,
            name = "A random name"
        )
    }
}
