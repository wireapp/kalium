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
import kotlin.time.Duration.Companion.days

/**
 * Mark the MLS End-to-End Identity enabling status change as notified
 * need to be called after notifying the user about the change
 * e.g. after showing a dialog, or a toast etc.
 */
interface MarkEnablingOfMLSE2EIAsNotifiedUseCase {
    suspend operator fun invoke()
}

internal class MarkEnablingOfMLSE2EIAsNotifiedUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : MarkEnablingOfMLSE2EIAsNotifiedUseCase {

    override suspend fun invoke() {
        userConfigRepository.snoozeE2EINotification(SNOOZE_MLS_ENABLE_CHANGE_MS)
    }

    companion object {
        val SNOOZE_MLS_ENABLE_CHANGE_MS = 1.days
    }
}
