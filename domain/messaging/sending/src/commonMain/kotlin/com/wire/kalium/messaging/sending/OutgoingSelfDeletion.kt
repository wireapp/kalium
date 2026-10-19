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
import kotlin.time.Duration

/**
 * The self-deletion timer of an outgoing message outside cells conversations: the team's setting decides
 * first, then the conversation's group timer, then the user's own timer when [considerSelfUserSettings].
 * A [teamTimer] that cannot be read leaves the decision to the conversation, as an enabled team does.
 */
fun outgoingSelfDeletionTimer(
    teamTimer: TeamSelfDeleteTimer?,
    groupTimer: Duration?,
    userTimer: Duration?,
    considerSelfUserSettings: Boolean,
): SelfDeletionTimer = when (teamTimer) {
    TeamSelfDeleteTimer.Disabled -> SelfDeletionTimer.Disabled
    is TeamSelfDeleteTimer.Enforced -> SelfDeletionTimer.Enforced.ByTeam(teamTimer.enforcedDuration)
    TeamSelfDeleteTimer.Enabled, null -> when {
        groupTimer != null && groupTimer > Duration.ZERO -> SelfDeletionTimer.Enforced.ByGroup(groupTimer)
        considerSelfUserSettings && userTimer != null && userTimer > Duration.ZERO -> SelfDeletionTimer.Enabled(userTimer)
        else -> SelfDeletionTimer.Enabled(null)
    }
}
