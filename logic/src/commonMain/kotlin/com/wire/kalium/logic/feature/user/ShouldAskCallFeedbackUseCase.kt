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
 *
 */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.util.DateTimeUtil
import kotlinx.datetime.Instant

/**
 * Use case to determine if the call feedback should be asked.
 */
interface ShouldAskCallFeedbackUseCase {
    suspend operator fun invoke(
        establishedTime: Instant?,
        currentTime: Instant = DateTimeUtil.currentInstant()
    ): ShouldAskCallFeedbackUseCaseResult
}

@Suppress("FunctionNaming")
internal fun ShouldAskCallFeedbackUseCase(
    userConfigRepository: UserConfigRepository
) = object : ShouldAskCallFeedbackUseCase {

    override suspend fun invoke(
        establishedTime: Instant?,
        currentTime: Instant
    ): ShouldAskCallFeedbackUseCaseResult {
        val callDurationInSeconds = establishedTime?.let {
            DateTimeUtil.calculateMillisDifference(it, currentTime) / MILLIS_IN_SECOND
        } ?: 0L

        return when {
            callDurationInSeconds in 1..<ONE_MINUTE -> {
                ShouldAskCallFeedbackUseCaseResult.ShouldNotAskCallFeedback.CallDurationIsLessThanOneMinute(callDurationInSeconds)
            }

            !isNextTimeForCallFeedbackReached() -> {
                ShouldAskCallFeedbackUseCaseResult.ShouldNotAskCallFeedback.NextTimeForCallFeedbackIsNotReached(callDurationInSeconds)
            }

            else -> {
                ShouldAskCallFeedbackUseCaseResult.ShouldAskCallFeedback(callDurationInSeconds)
            }
        }
    }

    private suspend fun isNextTimeForCallFeedbackReached(): Boolean {
        return userConfigRepository.getNextTimeForCallFeedback().map {
            it > 0L && DateTimeUtil.currentInstant() > Instant.fromEpochMilliseconds(it)
        }.getOrElse(true)
    }
}

sealed class ShouldAskCallFeedbackUseCaseResult {
    data class ShouldAskCallFeedback(val callDurationInSeconds: Long) : ShouldAskCallFeedbackUseCaseResult()
    sealed class ShouldNotAskCallFeedback(val reason: String) : ShouldAskCallFeedbackUseCaseResult() {
        data class CallDurationIsLessThanOneMinute(val callDurationInSeconds: Long) :
            ShouldNotAskCallFeedback("Call duration is less than 1 minute")

        data class NextTimeForCallFeedbackIsNotReached(val callDurationInSeconds: Long) :
            ShouldNotAskCallFeedback("Next time for call feedback is not reached")
    }
}

private const val MILLIS_IN_SECOND = 1_000L
private const val ONE_MINUTE = 60
