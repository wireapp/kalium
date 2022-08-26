package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.call.mapper.ParticipantMapperImpl
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantMapperTest {

    private val participantMapperImpl = ParticipantMapperImpl()

    @Test
    fun whenMappingToParticipant_withCallMember_thenReturnParticipant() = runTest {
        val participantMap = participantMapperImpl.fromCallMemberToParticipant(
            member = DUMMY_CALL_MEMBER
        )

        val expectedParticipant = Participant(
            id = QualifiedID(
                value = "dummyId",
                domain = "dummyDomain"
            ),
            clientId = "dummyClientId",
            isMuted = false,
            isCameraOn = false
        )

        assertEquals(expectedParticipant, participantMap)
    }

    @Test
    fun whenMappingToCallClient_withCallMember_thenReturnCallMember() = runTest {
        val callClientMap = participantMapperImpl.fromCallMemberToCallClient(
            member = DUMMY_CALL_MEMBER
        )

        val expectedCallClient = CallClient(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId"
        )

        assertEquals(expectedCallClient, callClientMap)
    }

    companion object {
        private val DUMMY_CALL_MEMBER = CallMember(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId",
            aestab = 0,
            vrecv = 0,
            isMuted = 0
        )
    }
}
