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
            isMuted = false,
            hasEstablishedAudio = true
        )
        val participant2 = Participant(
            id = UserId("participant2", "domain"),
            clientId = "clientId",
            name = "Max",
            isCameraOn = true,
            isMuted = false,
            hasEstablishedAudio = true
        )
        val participant3 = Participant(
            id = UserId("participant3", "domain"),
            clientId = "clientId",
            name = "Hisoka",
            isCameraOn = true,
            isMuted = false,
            hasEstablishedAudio = true
        )
        val participants = listOf(participant2, participant1, participant3)

        val result = participantsOrderByName.sortItems(participants)

        assertEquals(3, result.size)
        assertEquals(participant1, result.first())
        assertEquals(participant2, result.last())

    }
}
