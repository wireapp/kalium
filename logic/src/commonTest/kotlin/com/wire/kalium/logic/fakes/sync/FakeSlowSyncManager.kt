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

import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion

internal class FakeSlowSyncManager(
    val fakeSyncFlow: MutableSharedFlow<SlowSyncStatus> = MutableStateFlow(SlowSyncStatus.Pending)
) : SlowSyncManager {
    var resetRetryBackoffCount: Int = 0
        private set

    var performSyncFlowCount: Int = 0
        private set

    var cancelledSyncFlowCount: Int = 0
        private set

    override fun performSyncFlow(): Flow<SlowSyncStatus> = fakeSyncFlow
        .onCompletion { cause ->
            if (cause is CancellationException) cancelledSyncFlowCount++
        }
        .also { performSyncFlowCount++ }

    override fun resetRetryBackoff() {
        resetRetryBackoffCount++
    }
}
