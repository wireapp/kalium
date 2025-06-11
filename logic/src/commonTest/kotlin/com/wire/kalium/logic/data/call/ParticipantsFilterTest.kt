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

import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import io.mockative.every
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantsFilterTest {

        private val qualifiedIdMapper = mock(QualifiedIdMapper::class)

    lateinit var participantsFilter: ParticipantsFilter

    @BeforeTest
    fun setUp() {
        participantsFilter = ParticipantsFilterImpl(qualifiedIdMapper)

        every {
            qualifiedIdMapper.fromStringToQualifiedID(selfUserIdString)
        }.returns(selfUserId)

        every {
            qualifiedIdMapper.fromStringToQualifiedID(userId2String)
        }.returns(userId2)

        every {
            qualifiedIdMapper.fromStringToQualifiedID(userId3String)
        }.returns(userId3)
    }

    @Test
    fun givenAListOfParticipantsAndUserId_whenGettingOtherParticipants_thenReturnParticipantsExceptSelfParticipant() {

        val result = participantsFilter.otherParticipants(participants, selfClientId)

        assertEquals(3, result.size)
        assertEquals(participant2, result.first())
        assertEquals(participant11, result.last())
    }

    @Test
    fun givenAListOfParticipants_whenGettingSelfParticipant_thenReturnCorrectParticipant() {

        val result = participantsFilter.selfParticipant(participants, selfUserId, selfClientId)

        assertEquals(participant1, result)
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
        const val selfClientId = "1243545623"
        const val selfUserIdString = "participant1@domain"
        val selfUserId = UserId("participant1", "domain")

        const val userId2String = "participant2@domain"
        val userId2 = UserId("participant2", "domain")

        const val userId3String = "participant3@domain"
        val userId3 = UserId("participant3", "domain")

        val participant1 = Participant(
            id = selfUserId,
            clientId = selfClientId,
            name = "Alok",
            isCameraOn = true,
            isMuted = false,
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant11 = Participant(
            id = selfUserId,
            clientId = "clientId2",
            name = "Alok",
            isCameraOn = false,
            isMuted = false,
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant2 = Participant(
            id = userId2,
            clientId = "clientId2",
            name = "Max",
            isCameraOn = true,
            isMuted = false,
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant3 = Participant(
            id = userId3,
            clientId = "clientId3",
            name = "Hisoka",
            isCameraOn = false,
            isMuted = false,
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participants = listOf(participant1, participant2, participant3, participant11)
    }
}
