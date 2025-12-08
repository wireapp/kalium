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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.sync.SyncOutboxRepository

/**
 * Use case to enable or disable database sync replication.
 * When enabled, database changes are queued in the outbox for upload to server.
 * When disabled, triggers stop recording changes (no storage impact).
 */
interface EnableSyncReplicationUseCase {
    /**
     * @param enabled true to enable sync, false to disable
     * @return Either a [CoreFailure] or Unit on success
     */
    suspend operator fun invoke(enabled: Boolean): Either<CoreFailure, Unit>
}

internal class EnableSyncReplicationUseCaseImpl(
    private val syncOutboxRepository: SyncOutboxRepository
) : EnableSyncReplicationUseCase {
    override suspend fun invoke(enabled: Boolean): Either<CoreFailure, Unit> {
        return syncOutboxRepository.setSyncEnabled(enabled)
    }
}
