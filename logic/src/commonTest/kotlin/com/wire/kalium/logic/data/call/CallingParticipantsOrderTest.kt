package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserRepository
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.times
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallingParticipantsOrderTest {

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    @Mock
    private val participantsFilter = mock(classOf<ParticipantsFilter>())

    @Mock
    private val participantsOrderByName = mock(classOf<ParticipantsOrderByName>())


    private lateinit var callingParticipantsOrder: CallingParticipantsOrder

    @BeforeTest
    fun setup() {
        callingParticipantsOrder = CallingParticipantsOrderImpl(userRepository, participantsFilter, participantsOrderByName)
    }

    @Test
    fun givenAnEmptyListOfParticipants_whenOrderingParticipants_thenDoNotOrderItemsAndReturnEmptyList() = runTest {
        val emptyParticipantsList = listOf<Participant>()

        val result = callingParticipantsOrder.reorderItems(emptyParticipantsList)

        assertEquals(emptyParticipantsList, result)
    }

    @Test
    fun givenAListOfParticipants_whenOrderingParticipants_thenOrderItemsAlphabeticallyByNameExceptFirstOne() = runTest {
        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(selfUserId)

        given(participantsFilter).function(participantsFilter::participantsWithoutUserId)
            .whenInvokedWith(eq(participants), eq(selfUserId))
            .thenReturn(otherParticipants)

        given(participantsFilter).function(participantsFilter::selfParticipants)
            .whenInvokedWith(eq(participants), eq(selfUserId))
            .thenReturn(selfParticipants)

        given(participantsFilter).function(participantsFilter::participantsWithScreenSharingOn)
            .whenInvokedWith(eq(participants))
            .thenReturn(listOf())

        given(participantsFilter).function(participantsFilter::participantsByCamera)
            .whenInvokedWith(eq(otherParticipants), eq(true))
            .thenReturn(participantsWithCameraOn)

        given(participantsFilter).function(participantsFilter::participantsByCamera)
            .whenInvokedWith(eq(otherParticipants), eq(false))
            .thenReturn(participantsWithCameraOff)

        given(participantsOrderByName).function(participantsOrderByName::sortItems)
            .whenInvokedWith(eq(participantsWithCameraOff))
            .thenReturn(listOf(participant4))

        given(participantsOrderByName).function(participantsOrderByName::sortItems)
            .whenInvokedWith(eq(participantsWithCameraOn))
            .thenReturn(listOf(participant2, participant3))

        given(participantsOrderByName).function(participantsOrderByName::sortItems)
            .whenInvokedWith(eq(listOf()))
            .thenReturn(listOf())

        val result = callingParticipantsOrder.reorderItems(participants)

        assertEquals(participants.size, result.size)
        assertEquals(participant1, result.first())
        assertEquals(participant11, result[1])

        verify(userRepository).function(userRepository::getSelfUserId)
            .wasInvoked(exactly = once)

        verify(participantsFilter).function(participantsFilter::participantsWithoutUserId)
            .with(eq(participants), eq(selfUserId))
            .wasInvoked(exactly = once)

        verify(participantsFilter).function(participantsFilter::selfParticipants)
            .with(eq(participants), eq(selfUserId))
            .wasInvoked(exactly = once)

        verify(participantsFilter).function(participantsFilter::participantsWithScreenSharingOn)
            .with(eq(participants))
            .wasInvoked(exactly = once)

        verify(participantsFilter).function(participantsFilter::participantsByCamera)
            .with(any(), any())
            .wasInvoked(exactly = twice)

        verify(participantsOrderByName).function(participantsOrderByName::sortItems)
            .with(any())
            .wasInvoked(exactly = 3.times)
    }

    companion object {
        private val selfUserId = QualifiedID("participant1", "domain")
        val participant1 = Participant(
            id = selfUserId,
            clientId = "client1",
            isMuted = false,
            isCameraOn = false,
            name = "self user"
        )
        val participant2 = Participant(
            id = QualifiedID("participant2", "domain"),
            clientId = "client2",
            isMuted = false,
            isCameraOn = true,
            name = "user name"
        )
        val participant3 = Participant(
            id = QualifiedID("participant3", "domain"),
            clientId = "client3",
            isMuted = false,
            isCameraOn = true,
            name = "A random name"
        )
        val participant4 = Participant(
            id = QualifiedID("participant4", "domain"),
            clientId = "client3",
            isMuted = false,
            isCameraOn = false,
            name = "A random name"
        )
        val participant11 = Participant(
            id = selfUserId,
            clientId = "client11",
            isMuted = false,
            isCameraOn = false,
            name = "self user"
        )
        val participants = listOf(participant1, participant2, participant3, participant4, participant11)
        val selfParticipants = listOf(participant1, participant11)
        val otherParticipants = listOf(participant2, participant3, participant4)
        val participantsWithCameraOn = listOf(participant2, participant3)
        val participantsWithCameraOff = listOf(participant4)

    }
}
