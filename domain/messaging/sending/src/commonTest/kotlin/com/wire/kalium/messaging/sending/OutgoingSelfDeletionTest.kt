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

package com.wire.kalium.messaging.sending

import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class OutgoingSelfDeletionTest {

    @Test
    fun givenTheTeamDisablesSelfDeletion_thenConversationTimersAreIgnored() {
        assertEquals(SelfDeletionTimer.Disabled, timer(TeamSelfDeleteTimer.Disabled, group = 1.minutes, user = 5.seconds))
    }

    @Test
    fun givenTheTeamEnforcesATimer_thenItWinsOverTheGroupTimer() {
        assertEquals(
            SelfDeletionTimer.Enforced.ByTeam(1.minutes),
            timer(TeamSelfDeleteTimer.Enforced(1.minutes), group = 5.seconds, user = null)
        )
    }

    @Test
    fun givenAGroupTimer_thenItIsEnforcedOverTheUserTimer() {
        assertEquals(SelfDeletionTimer.Enforced.ByGroup(1.minutes), timer(TeamSelfDeleteTimer.Enabled, group = 1.minutes, user = 5.seconds))
    }

    @Test
    fun givenOnlyAUserTimer_thenItAppliesOnlyWhenUserSettingsCount() {
        assertEquals(SelfDeletionTimer.Enabled(5.seconds), timer(TeamSelfDeleteTimer.Enabled, group = null, user = 5.seconds))
        assertEquals(SelfDeletionTimer.Enabled(null), timer(TeamSelfDeleteTimer.Enabled, group = null, user = 5.seconds, self = false))
    }

    @Test
    fun givenUnreadableTeamSettings_thenTheConversationDecides() {
        assertEquals(SelfDeletionTimer.Enforced.ByGroup(1.minutes), timer(null, group = 1.minutes, user = null))
        assertEquals(SelfDeletionTimer.Enabled(null), timer(null, group = Duration.ZERO, user = null))
    }

    private fun timer(team: TeamSelfDeleteTimer?, group: Duration?, user: Duration?, self: Boolean = true) =
        outgoingSelfDeletionTimer(team, group, user, self)
}
