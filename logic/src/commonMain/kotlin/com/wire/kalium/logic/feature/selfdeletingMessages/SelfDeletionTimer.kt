/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.selfdeletingMessages

import com.wire.kalium.logic.data.id.ConversationId
import kotlin.time.Duration

sealed class SelfDeletionTimer {
    /**
     * Represents a self deletion timer that is currently disabled
     */
    object Disabled : SelfDeletionTimer()

    /**
     * Represents a self deletion timer that is enabled and can be changed/updated by the user
     */
    data class Enabled(val userDuration: Duration) : SelfDeletionTimer()

    /**
     * Represents a self deletion timer that is imposed by the team or conversation settings that can't be changed by the user
     * @param enforcedDuration the team or conversation imposed timer
     */
    data class Enforced(val enforcedDuration: Duration) : SelfDeletionTimer()

    fun toDuration(): Duration = when (this) {
        is Enabled -> userDuration
        is Enforced -> enforcedDuration
        else -> Duration.ZERO
    }

    val isEnforced
        get() = this is Enforced

    val isDisabled
        get() = this is Disabled
}

data class ConversationSelfDeletingTimer(
    val conversationId: ConversationId,
    val selfDeletionTimer: SelfDeletionTimer
)

data class TeamSettingsSelfDeletionStatus(
    val hasFeatureChanged: Boolean?,
    val enforcedSelfDeletionTimer: SelfDeletionTimer
)
