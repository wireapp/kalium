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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import io.mockative.Mockable

/**
 * Represents a handler for recovering from incremental sync.
 *
 * @see IncrementalSyncRecoveryHandlerImpl
 */
@Mockable
internal interface IncrementalSyncRecoveryHandler {
    suspend fun recover(failure: CoreFailure, onIncrementalSyncRetryCallback: OnIncrementalSyncRetryCallback)
}

internal fun interface OnIncrementalSyncRetryCallback {
    suspend fun retry()
}

/**
 * Implementation of the [IncrementalSyncRecoveryHandler] interface.
 *
 * It checks if the failure allows for recovery by checking if failure is a [CoreFailure.SyncEventOrClientNotFound].
 * If recovery is possible, it clears the last processed event ID and restarts the slow sync process.
 *
 * @property restartSlowSyncProcessForRecoveryUseCase The use case for restarting the slow sync process.
 * @property eventRepository The repository for accessing events.
 */
internal class IncrementalSyncRecoveryHandlerImpl(
    private val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase,
    private val eventRepository: EventRepository,
    logger: KaliumLogger = kaliumLogger,
) : IncrementalSyncRecoveryHandler {

    private val logger = logger.withFeatureId(SYNC)

    override suspend fun recover(failure: CoreFailure, onIncrementalSyncRetryCallback: OnIncrementalSyncRetryCallback) {
        logger.i("$TAG Checking if we can recover from the failure: $failure")
        if (shouldDiscardEventsAndRestartSlowSync(failure)) {
            logger.i("$TAG Discarding all events and restarting the slow sync process")
            eventRepository.clearLastSavedEventId().onSuccess {
                restartSlowSyncProcessForRecoveryUseCase()
            }
        }
        logger.i("$TAG Retrying to recover form the failure $failure, perform the incremental sync again")
        onIncrementalSyncRetryCallback.retry()
    }

    private fun shouldDiscardEventsAndRestartSlowSync(failure: CoreFailure): Boolean =
        failure is CoreFailure.SyncEventOrClientNotFound

    private companion object {
        private const val TAG = "IncrementalSyncRecoveryHandler"
    }

}
