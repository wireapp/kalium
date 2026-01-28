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

import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant

internal open class FakeSlowSyncRepository(
    override val slowSyncStatus: StateFlow<SlowSyncStatus> = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
) : SlowSyncRepository {
    override suspend fun setLastSlowSyncCompletionInstant(instant: Instant) {}
    override suspend fun clearLastSlowSyncCompletionInstant() {}
    override suspend fun setNeedsToRecoverMLSGroups(value: Boolean) {}
    override suspend fun needsToRecoverMLSGroups(): Boolean = false
    override suspend fun setNeedsToPersistHistoryLostMessage(value: Boolean) {}
    override suspend fun needsToPersistHistoryLostMessage(): Boolean = false
    override suspend fun observeLastSlowSyncCompletionInstant(): Flow<Instant?> = emptyFlow()
    override fun updateSlowSyncStatus(slowSyncStatus: SlowSyncStatus) {}
    override suspend fun setSlowSyncVersion(version: Int) {}
    override suspend fun getSlowSyncVersion(): Int? = null
}
