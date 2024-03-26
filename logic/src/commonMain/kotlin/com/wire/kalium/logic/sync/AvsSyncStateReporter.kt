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

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.collectLatest
import com.wire.kalium.logger.KaliumLogger

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
    val incrementalSyncRepository: IncrementalSyncRepository,
    kaliumLogger: KaliumLogger
) : AvsSyncStateReporter {

    private val logger = kaliumLogger.withTextTag("AvsSyncStateReporter")

    override suspend fun execute() {
        logger.d("Starting to monitor")
        incrementalSyncRepository.incrementalSyncState.collectLatest {
            when (it) {
                IncrementalSyncStatus.FetchingPendingEvents -> {
                    logger.d("Incremental sync started - Reporting that the app has started IncrementalSync to AVS")
                    callManager.value.reportProcessNotifications(true)
                }

                IncrementalSyncStatus.Live -> {
                    logger.d("Incremental sync done - Reporting that the app has finished IncrementalSync to AVS")
                    callManager.value.reportProcessNotifications(false)
                }

                is IncrementalSyncStatus.Failed -> {
                    logger.d("Incremental sync failed - Reporting that the app has finished IncrementalSync to AVS")
                    callManager.value.reportProcessNotifications(false)
                }

                IncrementalSyncStatus.Pending -> {
                    logger.d("Incremental sync Pending - Reporting that the app has finished IncrementalSync to AVS")
                    callManager.value.reportProcessNotifications(false)
                }
            }
        }
    }
}
