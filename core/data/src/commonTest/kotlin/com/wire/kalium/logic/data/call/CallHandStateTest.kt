/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.id.QualifiedID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CallHandStateTest {
    private val phone = CallHandIdentity(QualifiedID("user", "domain"), "phone")
    private val laptop = CallHandIdentity(phone.userId, "laptop")
    private val otherDomain = CallHandIdentity(QualifiedID("user", "other"), "phone")
    private fun time(seconds: Long) = Instant.fromEpochSeconds(seconds)
    private fun start() = CallHandState.start("first", time(10), setOf(phone, laptop, otherDomain))

    @Test
    fun devicesAndQualifiedUsersAreIndependent() {
        val state = start().receive(phone, true, time(11), "a")
            .receive(laptop, true, time(12), "b")
            .receive(otherDomain, true, time(13), "c")
            .receive(phone, false, time(14), "d")
        assertEquals(listOf(laptop, otherDomain), state.raised)
    }

    @Test
    fun duplicatesKeepPositionAndReraiseMovesToEnd() {
        val state = start().receive(phone, true, time(11), "a")
            .receive(laptop, true, time(12), "b")
            .receive(phone, true, time(13), "duplicate-raise")
        assertEquals(listOf(phone, laptop), state.raised)
        val lowered = state.receive(phone, false, time(14), "c").receive(phone, false, time(15), "d")
        assertEquals(listOf(laptop, phone), lowered.receive(phone, true, time(16), "e").raised)
    }

    @Test
    fun outOfOrderAndDuplicateSameTimestampEventsCannotRestoreLoweredHand() {
        val state = start().receive(phone, true, time(11), "raise")
            .receive(phone, false, time(11), "lower")
            .receive(phone, true, time(11), "raise")
            .receive(phone, true, time(10), "old")
        assertTrue(state.raised.isEmpty())
    }

    @Test
    fun rosterRefreshPreservesHandsButDepartureAndRejoinResetThem() {
        val raised = start().receive(phone, true, time(11), "raise")
        val refreshed = raised.withParticipants(setOf(phone, laptop, otherDomain), time(20))
        assertEquals(raised, refreshed)
        val rejoined = refreshed.withParticipants(setOf(laptop), time(21))
            .withParticipants(setOf(laptop, phone), time(23))
            .receive(phone, true, time(22), "old-after-departure")
        assertTrue(rejoined.raised.isEmpty())
        assertEquals(listOf(phone), rejoined.receive(phone, true, time(24), "new").raised)
    }

    @Test
    fun unknownDevicesAndEarlierCallEventsAreIgnored() {
        val fresh = CallHandState.start("second", time(30), setOf(phone))
            .receive(phone, true, time(29), "previous-call")
            .receive(laptop, true, time(31), "not-joined")
        assertTrue(fresh.raised.isEmpty())
        assertEquals("second", fresh.generation)
    }
}
