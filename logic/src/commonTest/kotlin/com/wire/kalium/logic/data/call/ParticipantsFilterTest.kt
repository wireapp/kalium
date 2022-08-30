package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantsFilterTest {

    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    lateinit var participantsFilter: ParticipantsFilter

    @BeforeTest
    fun setUp() {
        participantsFilter = ParticipantsFilterImpl(qualifiedIdMapper)

        given(qualifiedIdMapper).invocation { fromStringToQualifiedID(selfUserIdString) }
            .then { selfUserId }

        given(qualifiedIdMapper).invocation { fromStringToQualifiedID(userId2String) }
            .then { userId2 }

        given(qualifiedIdMapper).invocation { fromStringToQualifiedID(userId3String) }
            .then { userId3 }
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
        const val selfUserIdString = "participant1@domain"
        val selfUserId = UserId("participant1", "domain")

        const val userId2String = "participant2@domain"
        val userId2 = UserId("participant2", "domain")

        const val userId3String = "participant3@domain"
        val userId3 = UserId("participant3", "domain")

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
            id = userId2,
            clientId = "clientId",
            name = "Max",
            isCameraOn = true,
            isMuted = false
        )
        val participant3 = Participant(
            id = userId3,
            clientId = "clientId",
            name = "Hisoka",
            isCameraOn = false,
            isMuted = false
        )
        val participants = listOf(participant1, participant2, participant3, participant11)
    }
}
