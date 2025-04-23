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
package com.wire.kalium.logic.fakes.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.SyncStateObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSyncStateObserver : SyncStateObserver {
    val mutableSyncState = MutableStateFlow<SyncState>(SyncState.Waiting)

    override val syncState: StateFlow<SyncState> get() = mutableSyncState.asStateFlow()

    override suspend fun waitUntilLive() {
        TODO("Not yet implemented")
    }

    override suspend fun waitUntilLiveOrFailure(): Either<CoreFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun isSlowSyncOngoing(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun isSlowSyncCompleted(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun waitUntilStartedOrFailure(): Either<NetworkFailure.NoNetworkConnection, Unit> {
        TODO("Not yet implemented")
    }
}
