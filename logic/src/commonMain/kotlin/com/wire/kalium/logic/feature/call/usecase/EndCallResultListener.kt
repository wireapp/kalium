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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.user.ShouldAskCallFeedbackUseCaseResult
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Mockable
interface EndCallResultListener {
    suspend fun observeCallEndedResult(): Flow<EndCallResult>
    suspend fun onCallEndedBecauseOfVerificationDegraded()
    suspend fun onCallEndedAskForFeedback(shouldAskCallFeedback: ShouldAskCallFeedbackUseCaseResult)
}

/**
 * This singleton allow us to queue event to show dialog informing user that call was ended because of verification degradation.
 */
object EndCallResultListenerImpl : EndCallResultListener {

    private val conversationCallEnded = MutableSharedFlow<EndCallResult>()

    override suspend fun observeCallEndedResult(): Flow<EndCallResult> = conversationCallEnded

    override suspend fun onCallEndedBecauseOfVerificationDegraded() {
        conversationCallEnded.emit(EndCallResult.VerificationDegraded)
    }

    override suspend fun onCallEndedAskForFeedback(shouldAskCallFeedback: ShouldAskCallFeedbackUseCaseResult) {
        conversationCallEnded.emit(EndCallResult.AskForFeedback(shouldAskCallFeedback))
    }
}

sealed class EndCallResult {
    data object VerificationDegraded : EndCallResult()
    data class AskForFeedback(val shouldAskCallFeedback: ShouldAskCallFeedbackUseCaseResult) : EndCallResult()
}
