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
package com.wire.kalium.logic.sync

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * This class is responsible for reporting the current sync state to AVS.
 * It is used to report the start and end of the incremental sync.
 * Main reason for it is to let AVS know that the app is syncing and it should not send incoming call callbacks for those pending events
 */
internal interface AvsSyncStateReporter {
    suspend fun execute()
}

internal class AvsSyncStateReporterImpl(
    val callManager: Lazy<CallManager>,
    val observeSyncStateUseCase: ObserveSyncStateUseCase,
    kaliumLogger: KaliumLogger
) : AvsSyncStateReporter {

    private val logger = kaliumLogger.withTextTag("AvsSyncStateReporter")

    override suspend fun execute() {
        logger.d("Starting to monitor")
        observeSyncStateUseCase().distinctUntilChanged().collectLatest {
            when (it) {
                SyncState.GatheringPendingEvents -> {
                    logger.d("Reporting that the app has started IncrementalSync to AVS")
                    callManager.value.reportProcessNotifications(true)
                }

                SyncState.Live -> {
                    logger.d("Reporting that the app has finished IncrementalSync to AVS")
                    callManager.value.reportProcessNotifications(false)
                }

                else -> {}
            }
        }

    }
}
