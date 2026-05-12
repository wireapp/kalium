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

import com.wire.kalium.logic.data.call.CallRepository
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing the state of the cleanup of stale open calls.
 * This can happen, for example, when the app is killed while a call is ongoing, and the call is not properly closed.
 *
 * @return a flow of boolean, where true means that the cleanup is done, and false means that the cleanup is still in progress.
 */
public interface ObserveStaleOpenCallsCleanup {
    public operator fun invoke(): Flow<Boolean>
}

internal class ObserveStaleOpenCallsCleanupImpl(private val callRepository: CallRepository) : ObserveStaleOpenCallsCleanup {
    override fun invoke(): Flow<Boolean> = callRepository.observeStaleOpenCallsCleanupDone()
}
