/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.call.mapper.ActiveSpeakerMapperImpl
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveSpeakerMapperTest {

    private val activeSpeakerMapper = ActiveSpeakerMapperImpl()

    @Test
    fun givenUserAudioLevelNot0AndaudioLevelNowNot0_whenMapping_thenUserIsSpeaking() = runTest {
        val dummyParticipantWithDifferentClientId = DUMMY_PARTICIPANT.copy(
            clientId = "anotherClientId"
        )

        val callActiveSpeakerMap = activeSpeakerMapper.mapParticipantsActiveSpeaker(
            participants = listOf(
                DUMMY_PARTICIPANT,
                dummyParticipantWithDifferentClientId
            ),
            activeSpeakers = CallActiveSpeakers(
                activeSpeakers = listOf(
                    DUMMY_CALL_ACTIVE_SPEAKER.copy(audioLevel = 1, audioLevelNow = 1),
                    DUMMY_CALL_ACTIVE_SPEAKER1.copy(audioLevel = 1, audioLevelNow = 1)
                )
            )
        )

        val expectedParticipantsActiveSpeaker = listOf(
            DUMMY_PARTICIPANT.copy(
                isSpeaking = true
            ),
            dummyParticipantWithDifferentClientId.copy(
                isSpeaking = true
            )
        )

        assertEquals(expectedParticipantsActiveSpeaker, callActiveSpeakerMap)
    }


    @Test
    fun givenUserAudioLevelIs0AndaudioLevelNowNot0_whenMapping_thenUserIsSpeaking() = runTest {
        val dummyParticipantWithDifferentClientId = DUMMY_PARTICIPANT.copy(
            clientId = "anotherClientId"
        )

        val callActiveSpeakerMap = activeSpeakerMapper.mapParticipantsActiveSpeaker(
            participants = listOf(
                DUMMY_PARTICIPANT,
                dummyParticipantWithDifferentClientId
            ),
            activeSpeakers = CallActiveSpeakers(
                activeSpeakers = listOf(
                    DUMMY_CALL_ACTIVE_SPEAKER.copy(audioLevel = 0, audioLevelNow = 1),
                    DUMMY_CALL_ACTIVE_SPEAKER1.copy(audioLevel = 0, audioLevelNow = 1)
                )
            )
        )

        val expectedParticipantsActiveSpeaker = listOf(
            DUMMY_PARTICIPANT.copy(
                isSpeaking = true
            ),
            dummyParticipantWithDifferentClientId.copy(
                isSpeaking = true
            )
        )

        assertEquals(expectedParticipantsActiveSpeaker, callActiveSpeakerMap)
    }

    @Test
    fun givenUserAudioLevelNot0AndaudioLevelNowIs0_whenMapping_thenUserIsSpeaking() = runTest {
        val dummyParticipantWithDifferentClientId = DUMMY_PARTICIPANT.copy(
            clientId = "anotherClientId"
        )

        val callActiveSpeakerMap = activeSpeakerMapper.mapParticipantsActiveSpeaker(
            participants = listOf(
                DUMMY_PARTICIPANT,
                dummyParticipantWithDifferentClientId
            ),
            activeSpeakers = CallActiveSpeakers(
                activeSpeakers = listOf(
                    DUMMY_CALL_ACTIVE_SPEAKER.copy(audioLevel = 1, audioLevelNow = 0),
                    DUMMY_CALL_ACTIVE_SPEAKER1.copy(audioLevel = 1, audioLevelNow = 0)
                )
            )
        )

        val expectedParticipantsActiveSpeaker = listOf(
            DUMMY_PARTICIPANT.copy(
                isSpeaking = true
            ),
            dummyParticipantWithDifferentClientId.copy(
                isSpeaking = true
            )
        )

        assertEquals(expectedParticipantsActiveSpeaker, callActiveSpeakerMap)
    }

    @Test
    fun givenUserAudioLevelIs0AndaudioLevelNowIs0_whenMapping_thenUserIsNotSpeaking() = runTest {
        val dummyParticipantWithDifferentClientId = DUMMY_PARTICIPANT.copy(
            clientId = "anotherClientId"
        )

        val callActiveSpeakerMap = activeSpeakerMapper.mapParticipantsActiveSpeaker(
            participants = listOf(
                DUMMY_PARTICIPANT,
                dummyParticipantWithDifferentClientId
            ),
            activeSpeakers = CallActiveSpeakers(
                activeSpeakers = listOf(
                    DUMMY_CALL_ACTIVE_SPEAKER.copy(audioLevel = 0, audioLevelNow = 0),
                    DUMMY_CALL_ACTIVE_SPEAKER1.copy(audioLevel = 0, audioLevelNow = 0)
                )
            )
        )

        val expectedParticipantsActiveSpeaker = listOf(
            DUMMY_PARTICIPANT.copy(
                isSpeaking = false
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
            isSharingScreen = false,
            hasEstablishedAudio = true
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
