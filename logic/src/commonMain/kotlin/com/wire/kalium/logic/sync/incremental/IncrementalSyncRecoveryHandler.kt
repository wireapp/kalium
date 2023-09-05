/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase

internal interface IncrementalSyncRecoveryHandler {
    suspend fun recover(failure: CoreFailure, onIncrementalSyncRetryCallback: OnIncrementalSyncRetryCallback)
}

internal fun interface OnIncrementalSyncRetryCallback {
    suspend fun retry()
}

internal class IncrementalSyncRecoveryHandlerImpl(
    private val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase,
    private val eventRepository: EventRepository
) : IncrementalSyncRecoveryHandler {

    override suspend fun recover(failure: CoreFailure, onIncrementalSyncRetryCallback: OnIncrementalSyncRetryCallback) {
        kaliumLogger.i("$TAG Checking if we can recover from the failure: $failure")
        if (shouldRestartSlowSyncProcess(failure)) {
            eventRepository.fetchOldestAvailableEventId().flatMap { oldestEventId ->
                eventRepository.updateLastProcessedEventId(oldestEventId)
            }.onSuccess {
                restartSlowSyncProcessForRecoveryUseCase()
            }
        }
        kaliumLogger.i("$TAG Retrying to recover form the failure $failure, perform the incremental sync again")
        onIncrementalSyncRetryCallback.retry()
    }

    private fun shouldRestartSlowSyncProcess(failure: CoreFailure): Boolean =
        failure is CoreFailure.SyncEventOrClientNotFound

    private companion object {
        private const val TAG = "IncrementalSyncRecoveryHandler"
    }

}
