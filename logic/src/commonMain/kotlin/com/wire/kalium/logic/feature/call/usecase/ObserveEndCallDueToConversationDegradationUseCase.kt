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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * The useCase for observing when the ongoing call was ended because of degradation of conversation verification status (Proteus or MLS)
 */
interface ObserveEndCallDueToConversationDegradationUseCase {
    /**
     * @return [Flow] that emits only when the call was ended because of degradation of conversation verification status (Proteus or MLS)
     */
    suspend operator fun invoke(): Flow<Unit>
}

internal class ObserveEndCallDueToConversationDegradationUseCaseImpl(
    private val endCallListener: EndCallResultListener
) : ObserveEndCallDueToConversationDegradationUseCase {
    override suspend fun invoke(): Flow<Unit> =
        endCallListener.observeCallEndedResult()
            .filterIsInstance(EndCallResult.VerificationDegraded::class)
            .map { Unit }
}
