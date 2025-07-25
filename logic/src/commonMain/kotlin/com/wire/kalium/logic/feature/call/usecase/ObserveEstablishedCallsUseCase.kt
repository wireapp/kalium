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

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.Call
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing the established calls.
 */
@Mockable
interface ObserveEstablishedCallsUseCase {
    /**
     * That Flow emits everytime when the list is changed
     * @return a [Flow] of the list of established calls that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<Call>>
}

internal class ObserveEstablishedCallsUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
) : ObserveEstablishedCallsUseCase {
    override suspend operator fun invoke(): Flow<List<Call>> {
        return callRepository.establishedCallsFlow()
    }
}
