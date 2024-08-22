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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.UpdateNextTimeCallFeedbackUseCase.Companion.askingForFeedbackPeriod
import com.wire.kalium.util.DateTimeUtil
import kotlin.time.Duration.Companion.days

/**
 * Use case that updates next time when user should be asked for a feedback about call quality.
 */
interface UpdateNextTimeCallFeedbackUseCase {
    /**
     * Update next time when user should be asked for a feedback about call quality.
     * @param neverAskAgain [Boolean] if user checked "never ask me again"
     */
    suspend operator fun invoke(neverAskAgain: Boolean)

    companion object {
        val askingForFeedbackPeriod = 3.days
    }
}

@Suppress("FunctionNaming")
internal fun UpdateNextTimeCallFeedbackUseCase(
    userConfigRepository: UserConfigRepository
) = object : UpdateNextTimeCallFeedbackUseCase {

    override suspend fun invoke(neverAskAgain: Boolean) {
        val nextTimestamp = if (neverAskAgain) -1L
        else DateTimeUtil.currentInstant().plus(askingForFeedbackPeriod).toEpochMilliseconds()

        userConfigRepository.updateNextTimeForCallFeedback(nextTimestamp)
    }

}
