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

import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeIncrementalSyncManager(
    val fakeSyncFlow: MutableSharedFlow<IncrementalSyncStatus> = MutableStateFlow(IncrementalSyncStatus.Pending)
) : IncrementalSyncManager {
    override fun performSyncFlow(): Flow<IncrementalSyncStatus> = fakeSyncFlow
}
