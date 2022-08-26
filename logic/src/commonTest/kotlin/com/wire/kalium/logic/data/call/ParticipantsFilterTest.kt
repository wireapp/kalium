package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.user.UserId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantsFilterTest {

    lateinit var participantsFilter: ParticipantsFilter

    @BeforeTest
    fun setUp() {
        participantsFilter = ParticipantsFilterImpl()
    }

    @Test
    fun givenAListOfParticipantsAndUserId_whenCallingParticipantsWithoutUserId_thenReturnParticipantsWithoutThatUser() {

        val result = participantsFilter.participantsWithoutUserId(participants, selfUserId)

        assertEquals(2, result.size)
        assertEquals(participant2, result.first())
        assertEquals(participant3, result.last())
    }

    @Test
    fun givenAListOfParticipantsAndUserId_whenCallingParticipantsWithUserIdOnly_thenReturnParticipantsWithThatIdOnly() {

        val result = participantsFilter.selfParticipants(participants, selfUserId)

        assertEquals(2, result.size)
        assertEquals(participant1, result.first())
        assertEquals(participant11, result.last())
    }

    @Test
    fun givenAListOfParticipants_whenFilteringWithCameraOn_thenReturnParticipantsWithCameraOn() {
        val result = participantsFilter.participantsByCamera(participants, true)

        assertEquals(2, result.size)
        assertEquals(participant1, result.first())
        assertEquals(participant2, result.last())
    }

    @Test
    fun givenAListOfParticipants_whenFilteringWithCameraOff_thenReturnParticipantsWithCameraOff() {
        val result = participantsFilter.participantsByCamera(participants, false)

        assertEquals(2, result.size)
        assertEquals(participant3, result.first())
        assertEquals(participant11, result.last())

    }

    companion object {
        val selfUserId = UserId("participant1", "domain")

        val participant1 = Participant(
            id = selfUserId,
            clientId = "clientId1",
            name = "Alok",
            isCameraOn = true,
            isMuted = false
        )
        val participant11 = Participant(
            id = selfUserId,
            clientId = "clientId2",
            name = "Alok",
            isCameraOn = false,
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
            isCameraOn = false,
            isMuted = false
        )
        val participants = listOf(participant1, participant2, participant3, participant11)
    }
}
