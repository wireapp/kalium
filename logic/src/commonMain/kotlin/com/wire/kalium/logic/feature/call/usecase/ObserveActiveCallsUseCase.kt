/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
 * This use case is responsible for observing the active calls.
 * Active calls are the calls that are either outgoing, ongoing or incoming, so one of these states:
 * STARTED, INCOMING, ANSWERED, ESTABLISHED, STILL_ONGOING
 */
public interface ObserveActiveCallsUseCase {
    public operator fun invoke(): Flow<List<Call>>
}

internal class ObserveActiveCallsUseCaseImpl(private val callRepository: CallRepository) : ObserveActiveCallsUseCase {
    override fun invoke(): Flow<List<Call>> = callRepository.activeCallsFlow()
}
