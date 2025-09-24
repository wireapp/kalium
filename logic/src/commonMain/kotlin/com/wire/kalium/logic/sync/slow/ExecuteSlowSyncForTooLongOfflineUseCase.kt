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

import co.touchlab.stately.concurrency.AtomicBoolean
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import io.mockative.Mockable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import kotlin.time.Duration.Companion.seconds

/**
 * Execute slow sync because the client was offline for too long.
 * It will wait until a slow sync is completed and return the instant of completion.
 */
@Mockable
internal interface ExecuteSlowSyncForTooLongOfflineUseCase {
    suspend operator fun invoke(onComplete: suspend () -> Either<CoreFailure, Unit>)
}

internal class ExecuteSlowSyncForTooLongOfflineUseCaseImpl(
    private val slowSyncRepository: SlowSyncRepository,
    logger: KaliumLogger
) : ExecuteSlowSyncForTooLongOfflineUseCase {

    val logger = logger.withTextTag("ExecuteSlowSyncForTooLongOfflineUseCase")
    val isRunning = AtomicBoolean(false)

    override suspend operator fun invoke(onComplete: suspend () -> Either<CoreFailure, Unit>) {
        if (!isRunning.value) {
            logger.d("Set needs to persist history lost message to true and clear last slow sync instant")
            slowSyncRepository.setNeedsToPersistHistoryLostMessage(true)
            slowSyncRepository.clearLastSlowSyncCompletionInstant()
        }
        logger.d("Starting slow sync for too long offline")
        isRunning.value = true

        delay(1.seconds) // wait a bit before observing better safe than sorry
        slowSyncRepository.observeLastSlowSyncCompletionInstant()
            .filterNotNull()
            .take(1)
            .collect { instant ->
                logger.d("Slow sync completed at $instant, calling onComplete")
                onComplete()
                isRunning.value = false
            }
    }
}
