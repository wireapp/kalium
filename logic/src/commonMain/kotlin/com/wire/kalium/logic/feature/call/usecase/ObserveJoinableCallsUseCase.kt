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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes calls that can be joined from conversation UI.
 */
public interface ObserveJoinableCallsUseCase {
    public operator fun invoke(): Flow<List<Call>>
}

internal class ObserveJoinableCallsUseCaseImpl(
    private val callRepository: CallRepository
) : ObserveJoinableCallsUseCase {

    override fun invoke(): Flow<List<Call>> = callRepository.joinableCallsFlow()
}
