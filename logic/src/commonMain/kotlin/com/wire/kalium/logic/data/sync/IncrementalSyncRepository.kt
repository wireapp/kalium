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

package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository.Companion.BUFFER_SIZE
import com.wire.kalium.common.logger.kaliumLogger
import io.mockative.Mockable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Mockable
internal interface IncrementalSyncRepository {
    /**
     * Buffered flow of [IncrementalSyncStatus].
     * - Has a replay size of 1, so the latest
     * value is always immediately available for new observers.
     * - Doesn't emit repeated values.
     * - It has a limited buffer of size [BUFFER_SIZE]
     * that will drop the oldest values if the buffer is full
     * to prevent emissions from being suspended due to slow
     * collectors.
     * @see [BufferOverflow]
     */
    val incrementalSyncState: Flow<IncrementalSyncStatus>

    suspend fun updateIncrementalSyncState(newState: IncrementalSyncStatus)

    /**
     * Returns the timestamp of the last received WebSocket event.
     * Used to detect stale WebSocket connections that stopped receiving events
     * without proper disconnection notification.
     *
     * @return The [Instant] when the last WebSocket event was received, or null if no events were received yet.
     */
    fun lastWebSocketEventInstant(): Instant?

    /**
     * Records the current timestamp as the last WebSocket event time.
     * Should be called whenever a WebSocket event is received.
     */
    fun recordLastWebSocketEvent()

    companion object {
        // The same default buffer size used by Coroutines channels
        const val BUFFER_SIZE = 64
    }
}

internal class InMemoryIncrementalSyncRepository(
    logger: KaliumLogger = kaliumLogger,
) : IncrementalSyncRepository {

    private val logger = logger.withFeatureId(SYNC)

    private val _syncState = MutableSharedFlow<IncrementalSyncStatus>(
        replay = 1,
        extraBufferCapacity = BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _lastWebSocketEventInstant = MutableStateFlow<Instant?>(null)

    override val incrementalSyncState = _syncState
        .asSharedFlow()
        .distinctUntilChanged()

    init {
        _syncState.tryEmit(IncrementalSyncStatus.Pending)
    }

    override suspend fun updateIncrementalSyncState(newState: IncrementalSyncStatus) {
        logger.i("IncrementalSyncStatus Updated FROM:${_syncState.first()}; TO: $newState")
        _syncState.emit(newState)
    }

    override fun lastWebSocketEventInstant(): Instant? = _lastWebSocketEventInstant.value

    override fun recordLastWebSocketEvent() {
        _lastWebSocketEventInstant.value = Clock.System.now()
    }
}
