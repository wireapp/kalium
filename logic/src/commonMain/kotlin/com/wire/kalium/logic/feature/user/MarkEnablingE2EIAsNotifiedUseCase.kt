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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Mark the MLS End-to-End Identity enabling status change as notified
 * need to be called after notifying the user about the change
 * e.g. after showing a dialog, or a toast etc.
 */
interface MarkEnablingE2EIAsNotifiedUseCase {
    suspend operator fun invoke(timeLeft: Duration)
}

internal class MarkEnablingE2EIAsNotifiedUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : MarkEnablingE2EIAsNotifiedUseCase {

    override suspend fun invoke(timeLeft: Duration) {
        userConfigRepository.snoozeE2EINotification(snoozeTime(timeLeft))
    }

    private fun snoozeTime(timeLeft: Duration): Duration =
        when {
            timeLeft > 1.days -> 1.days
            timeLeft > 4.hours -> 4.hours
            timeLeft > 1.hours -> 1.hours
            timeLeft > 15.minutes -> 15.minutes
            else -> 5.minutes
        }
}
