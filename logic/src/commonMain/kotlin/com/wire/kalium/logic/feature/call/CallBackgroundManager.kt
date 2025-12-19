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
package com.wire.kalium.logic.feature.call

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.SyncStateObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal interface CallBackgroundManager {
    fun setBackground(background: Boolean) {}
    suspend fun startProcessing() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class CallBackgroundManagerImpl(
    private val callManager: Lazy<CallManager>,
    private val syncStateObserver: Lazy<SyncStateObserver>,
    private val selfUserId: UserId,
    private val logger: KaliumLogger = kaliumLogger,
    private val initialBackgroundState: Boolean = false
) : CallBackgroundManager {
    private val tagWithUserId = "$TAG(${selfUserId.toLogString()})"
    private val backgroundState: MutableStateFlow<Boolean> = MutableStateFlow(initialBackgroundState)

    override fun setBackground(background: Boolean) {
        backgroundState.value = background
    }

    override suspend fun startProcessing() {
        backgroundState
            .flatMapLatest { background ->
                logger.i("$tagWithUserId: app background state changed: $background")
                when (background) {
                    false -> flowOf(false) // not in background
                    true -> syncStateObserver.value.syncState.map { syncState ->
                        logger.i("$tagWithUserId: app sync state changed: $syncState")
                        when (syncState) {
                            is SyncState.Failed -> true // in background and connection is down due to failure
                            is SyncState.Waiting -> true // in background and connection is down due to waiting for network
                            is SyncState.SlowSync -> false // in background, syncing initial data, so connection is up
                            is SyncState.GatheringPendingEvents -> false // in background, gathering pending events, so connection is up
                            is SyncState.Live -> false // in background, live, so connection is up
                        }
                    }
                }
            }
            .distinctUntilChanged()
            .collectLatest(callManager.value::setBackground)
    }

    companion object {
        private const val TAG = "CallBackgroundManager"
    }
}
