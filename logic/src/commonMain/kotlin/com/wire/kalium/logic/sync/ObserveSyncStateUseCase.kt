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

package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.SyncState
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

/**
 * Allows observing of [SyncState].
 * A value is always available immediately for new observers.
 * Assumes that old SyncStates are not relevant anymore, so the available [Flow]
 * has a limited buffer size and will drop the oldest values as there's no point
 * in waiting for slow collectors.
 * In case a slow collector is interested in receiving all values, it should add a buffer of its own.
 */
@Mockable
interface ObserveSyncStateUseCase {
    operator fun invoke(): Flow<SyncState>
}

internal class ObserveSyncStateUseCaseImpl internal constructor(
    private val syncStateObserver: SyncStateObserver
) : ObserveSyncStateUseCase {

    override operator fun invoke(): Flow<SyncState> = syncStateObserver.syncState
}
