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
package com.wire.kalium.work

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class InitialSyncWorkMonitor(
    private val scope: CoroutineScope,
    private val isSlowSyncOngoingFlow: Flow<Boolean>,
    private val repository: WorkRepository
) {

    fun startMonitoring() {
        scope.launch {
            isSlowSyncOngoingFlow.collect { isSlowSyncOngoing ->
                val status = if (isSlowSyncOngoing) {
                    Work.Status.InProgress
                } else {
                    Work.Status.Complete
                }
                repository.addOrUpdateWork(Work(WorkId.INITIAL_SYNC, Work.Type.InitialSync, status))
            }
        }
    }

}
