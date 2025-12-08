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

package com.wire.kalium.logic.feature.sync

import com.wire.kalium.logic.data.sync.SyncOutboxRepository
import com.wire.kalium.logic.data.sync.SyncOutboxStats
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe sync outbox statistics (counts by status).
 * Useful for displaying sync status in UI (e.g., pending operations count).
 */
interface ObserveSyncOutboxStatsUseCase {
    /**
     * @return Flow of [SyncOutboxStats] that emits whenever outbox changes
     */
    operator fun invoke(): Flow<SyncOutboxStats>
}

internal class ObserveSyncOutboxStatsUseCaseImpl(
    private val syncOutboxRepository: SyncOutboxRepository
) : ObserveSyncOutboxStatsUseCase {
    override fun invoke(): Flow<SyncOutboxStats> {
        return syncOutboxRepository.observeOutboxStats()
    }
}
