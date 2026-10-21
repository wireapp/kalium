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

import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

public class LongWorkScope private constructor(
    private val userScopeProvider: () -> CoroutineScope,
    private val isSlowSyncOngoingFlowProvider: () -> Flow<Boolean>,
    startImmediately: Boolean,
) {
    /** Preserve immediate monitoring for callers of the existing public constructor. */
    public constructor(
        userScopeProvider: () -> CoroutineScope,
        isSlowSyncOngoingFlowProvider: () -> Flow<Boolean>,
    ) : this(userScopeProvider, isSlowSyncOngoingFlowProvider, true)

    private val repository = InMemoryWorkRepository()
    private val started = MutableStateFlow(false)

    public val observeWorkStatus: ObserveWorkStatusUseCase = ObserveWorkStatusUseCase(repository)
    public val observeNewWorks: ObserveNewWorkUseCase = ObserveNewWorkUseCase(repository)

    init {
        if (startImmediately) startMonitoring()
    }

    /** The prepared account owner starts monitoring only after the session registry retains it. */
    @InternalKaliumApi
    public fun startInitialSyncMonitoring(): Unit = startMonitoring()

    private fun startMonitoring() {
        if (!started.compareAndSet(false, true)) return
        InitialSyncWorkMonitor(userScopeProvider(), isSlowSyncOngoingFlowProvider(), repository).startMonitoring()
    }

    public companion object {
        @InternalKaliumApi
        public fun prepareForRuntime(
            userScopeProvider: () -> CoroutineScope,
            isSlowSyncOngoingFlowProvider: () -> Flow<Boolean>,
        ): LongWorkScope = LongWorkScope(userScopeProvider, isSlowSyncOngoingFlowProvider, false)
    }
}
