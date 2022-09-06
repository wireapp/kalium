package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.call.mapper.ActiveSpeakerMapperImpl
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveSpeakerMapperTest {

    private val activeSpeakerMapper = ActiveSpeakerMapperImpl()

    @Test
    fun givenCallActiveSpeakers_whenMappingToParticipantsActiveSpeaker_thenReturnParticipantsActiveSpeaker() = runTest {
        val dummyParticipantWithDifferentClientId = DUMMY_PARTICIPANT.copy(
            clientId = "anotherClientId"
        )

        val callActiveSpeakerMap = activeSpeakerMapper.mapParticipantsActiveSpeaker(
            participants = listOf(
                DUMMY_PARTICIPANT,
                dummyParticipantWithDifferentClientId
            ),
            activeSpeakers = CallActiveSpeakers(
                activeSpeakers = listOf(DUMMY_CALL_ACTIVE_SPEAKER, DUMMY_CALL_ACTIVE_SPEAKER1)
            )
        )

        val expectedParticipantsActiveSpeaker = listOf(
            DUMMY_PARTICIPANT.copy(
                isSpeaking = true
            ),
            dummyParticipantWithDifferentClientId.copy(
                isSpeaking = false
            )
        )

        assertEquals(expectedParticipantsActiveSpeaker, callActiveSpeakerMap)
    }

    companion object {
        private val DUMMY_PARTICIPANT = Participant(
            id = QualifiedID(
                value = "dummyId",
                domain = "dummyDomain"
            ),
            clientId = "dummyClientId",
            isMuted = false,
            isSpeaking = false,
            isCameraOn = false,
            isSharingScreen = false
        )
        private val DUMMY_CALL_ACTIVE_SPEAKER = CallActiveSpeaker(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId",
            audioLevel = 1,
            audioLevelNow = 1
        )
        private val DUMMY_CALL_ACTIVE_SPEAKER1 = CallActiveSpeaker(
            userId = "dummyId@dummyDomain",
            clientId = "anotherClientId",
            audioLevel = 1,
            audioLevelNow = 0
        )
    }
}
