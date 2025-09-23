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
package com.wire.kalium.logic.sync.slow

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import io.mockative.Mockable
import kotlinx.coroutines.flow.first

/**
 * Execute slow sync because the client was offline for too long.
 * It will wait until a slow sync is completed and return the instant of completion.
 *
 */
@Mockable
internal interface ExecuteSlowSyncForTooLongOfflineUseCase {
    suspend operator fun invoke(onComplete: suspend () -> Either<CoreFailure, Unit>)
}

internal class ExecuteSlowSyncForTooLongOfflineUseCaseImpl(
    private val slowSyncRepository: SlowSyncRepository,
) :
    ExecuteSlowSyncForTooLongOfflineUseCase {

    override suspend operator fun invoke(onComplete: suspend () -> Either<CoreFailure, Unit>) {
//         println("execute slow sync for too long offline")
        slowSyncRepository.setNeedsToPersistHistoryLostMessage(true)
//         println("clear and observe until slow sync instant set")
        slowSyncRepository.clearAndObserveUntilSlowSyncInstantSet().first()
//         println("slow sync completed, calling onComplete")
        onComplete()
    }
}
