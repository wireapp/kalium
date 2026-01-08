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
package com.wire.kalium.logic.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.util.DelicateKaliumApi

public interface SyncRequest {

    /**
     * Suspends execution until the specified [syncState] is reached or a failure occurs.
     *
     * @see
     * @param syncState The desired [SyncState] to wait for.
     * @return [SyncRequestResult] that it's either a [SyncRequestResult.Failure] if [SyncState.Failed] is encountered,
     * or [SyncRequestResult.Success] if the specified [syncState] is reached.
     */
    public suspend fun waitUntilOrFailure(syncState: SyncState): SyncRequestResult

    /**
     * Shortcut for [waitUntilOrFailure] with Live state.
     * @see waitUntilOrFailure
     */
    public suspend fun waitUntilLiveOrFailure(): SyncRequestResult

    /**
     * When called, the sync process continues without being released.
     * This ensuring synchronization persists as long as the sync scope lives.
     * This is particularly useful for services that do not care about the lifecycle, like TestService, CLI, etc. and shouldn't
     * be used by applications that turn sync on/off, like Mobile apps.
     */
    @DelicateKaliumApi("By calling this, Sync will run indefinitely.")
    public fun keepSyncAlwaysOn()
}

public sealed class SyncRequestResult {
    public object Success : SyncRequestResult()
    public data class Failure(val error: CoreFailure) : SyncRequestResult()
}
