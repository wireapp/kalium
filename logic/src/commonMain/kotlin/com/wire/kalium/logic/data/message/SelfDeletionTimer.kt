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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.util.serialization.toJsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

sealed interface SelfDeletionTimer {
    val duration: Duration?

    /**
     * Represents a self deletion timer that is currently disabled
     */
    data object Disabled : SelfDeletionTimer {
        override val duration: Duration? = null
    }

    /**
     * Represents a self deletion timer that is enabled and can be changed/updated by the user
     */
    data class Enabled(override val duration: Duration?) : SelfDeletionTimer

    /**
     * Represents a self deletion timer that is imposed by the team or conversation settings that can't be changed by the user
     * @param enforcedDuration the team or conversation imposed timer
     */
    sealed interface Enforced : SelfDeletionTimer {
        data class ByTeam(override val duration: Duration) : Enforced
        data class ByGroup(override val duration: Duration) : Enforced
    }

    fun toLogString(eventDescription: String): String = toLogMap(eventDescription).toJsonElement().toString()

    val isEnforced
        get() = this is Enforced

    val isEnforcedByTeam
        get() = this is Enforced.ByTeam

    val isEnforcedByGroup
        get() = this is Enforced.ByGroup

    val isDisabled
        get() = this is Disabled

    private fun toLogMap(eventDescription: String): Map<String, Any?> = mapOf(
        eventKey to eventDescription,
        typeKey to this::class.simpleName,
        durationKey to duration?.inWholeSeconds,
        isEnforcedKey to isEnforced,
        isDisabledKey to isDisabled
    )

    companion object {
        const val SELF_DELETION_LOG_TAG = "Self-Deletion"
        private const val eventKey = "event"
        private const val typeKey = "selfDeletionTimerType"
        private const val durationKey = "durationInSeconds"
        private const val isEnforcedKey = "isEnforced"
        private const val isDisabledKey = "isDisabled"
    }
}

data class ConversationSelfDeletionStatus(
    val conversationId: ConversationId,
    val selfDeletionTimer: SelfDeletionTimer
)

data class TeamSettingsSelfDeletionStatus(
    /**
     * This value is used to inform the user that the team settings were changed. When true, an informative dialog will be shown. Once the
     * user acknowledges or dismisses it, the value will be set again to false. When null, this means that we still don't know the real
     * value of the flag, i.e. on initial sync
     * */
    val hasFeatureChanged: Boolean?,
    /**
     * The enforced self deletion timer for the whole team. Depending on the team settings, this value will override any the conversation or
     * user settings (aka, if the team settings timer is set to [SelfDeletionTimer.Enforced] or [SelfDeletionTimer.Disabled] then the user
     * won't be able to change the timer for any conversation
     * */
    val enforcedSelfDeletionTimer: TeamSelfDeleteTimer
)

sealed interface TeamSelfDeleteTimer {
    data object Disabled : TeamSelfDeleteTimer
    data object Enabled : TeamSelfDeleteTimer
    data class Enforced(val enforcedDuration: Duration) : TeamSelfDeleteTimer

    fun toLogMap(eventDescription: String): Map<String, Any?> = mapOf(
        eventKey to eventDescription,
        typeKey to this::class.simpleName,
        durationKey to if (this is Enforced) enforcedDuration.inWholeSeconds else ZERO,
        isEnforcedKey to (this is Enforced),
        isDisabledKey to (this is Disabled)
    )

    companion object {
        private const val eventKey = "event"
        private const val typeKey = "selfDeletionTimerType"
        private const val durationKey = "durationInSeconds"
        private const val isEnforcedKey = "isEnforced"
        private const val isDisabledKey = "isDisabled"
    }
}
