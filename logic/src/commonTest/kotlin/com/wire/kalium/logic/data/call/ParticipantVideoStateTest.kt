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

import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.call.mapper.ParticipantMapperImpl
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.framework.TestCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParticipantVideoStateTest {
    private val qualifiedIdMapper = QualifiedIdMapper(QualifiedID("self", "wire.test"))
    private val mapper = ParticipantMapperImpl(
        VideoStateCheckerImpl(), CallMapperImpl(qualifiedIdMapper), qualifiedIdMapper
    )

    @Test
    fun givenReceiveStates_whenMappingParticipants_thenOnlyExplicitPauseIsPaused() {
        val cases = listOf(
            ReceiveState(0, camera = false, sharing = false, paused = false),
            ReceiveState(1, camera = true, sharing = false, paused = false),
            ReceiveState(2, camera = true, sharing = false, paused = false),
            ReceiveState(3, camera = false, sharing = false, paused = true),
            ReceiveState(4, camera = false, sharing = true, paused = false),
            ReceiveState(5, camera = false, sharing = false, paused = false),
            ReceiveState(-1, camera = false, sharing = false, paused = false)
        )
        cases.forEach { state ->
            val minimized = mapper.fromCallMemberToParticipantMinimized(member.copy(vrecv = state.raw))
            val full = TestCall.oneOnOneCallMetadata().copy(participants = listOf(minimized)).getFullParticipants().single()
            assertEquals(state.paused, minimized.isVideoPaused, "minimized state ${state.raw}")
            assertEquals(state.paused, full.isVideoPaused, "full state ${state.raw}")
            assertEquals(state.camera, full.isCameraOn, "camera eligibility ${state.raw}")
            assertEquals(state.sharing, full.isSharingScreen, "sharing eligibility ${state.raw}")
            assertEquals(QualifiedID("remote", "wire.test"), full.id)
            assertEquals(member.clientId, full.clientId)
            assertEquals(true, full.isMuted)
            assertEquals(true, full.hasEstablishedAudio)
        }
    }

    @Test
    fun givenPauseResumeAndStop_whenReplacingRoster_thenPauseDoesNotPersist() {
        val participants = listOf(3, 1, 3, 0).map { raw ->
            mapper.fromCallMemberToParticipantMinimized(member.copy(vrecv = raw))
        }
        val metadata = TestCall.oneOnOneCallMetadata()
        val full = participants.map { metadata.copy(participants = listOf(it)).getFullParticipants().single() }
        assertEquals(listOf(true, false, true, false), full.map { it.isVideoPaused })
        assertEquals(listOf(false, true, false, false), full.map { it.isCameraOn })
        // Metadata flows compare participant lists. Paused and stopped must no longer compare equal.
        assertNotEquals(participants[2], participants[3])
    }

    @Test
    fun givenMatchingUsersAndDifferentClients_whenReorderingRoster_thenPauseFollowsQualifiedClient() {
        val members = listOf(
            member.copy(vrecv = 3),
            member.copy(clientId = "second", vrecv = 0),
            member.copy(userId = "remote@other.test", vrecv = 1)
        )
        val expected = mapOf(
            (QualifiedID("remote", "wire.test") to "first") to true,
            (QualifiedID("remote", "wire.test") to "second") to false,
            (QualifiedID("remote", "other.test") to "first") to false
        )
        listOf(members, members.reversed(), members.drop(1)).forEach { roster ->
            val minimized = roster.map(mapper::fromCallMemberToParticipantMinimized)
            val full = TestCall.oneOnOneCallMetadata().copy(participants = minimized).getFullParticipants()
            assertEquals(roster.size, full.size)
            full.forEach { assertEquals(expected[it.id to it.clientId], it.isVideoPaused) }
        }
    }

    private data class ReceiveState(val raw: Int, val camera: Boolean, val sharing: Boolean, val paused: Boolean)

    private companion object {
        val member = CallMember(userId = "remote@wire.test", clientId = "first", aestab = 1, vrecv = 0, isMuted = 1)
    }
}
