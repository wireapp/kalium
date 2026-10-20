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

/** Compatibility owner; the public constructor still starts initial-sync monitoring immediately. */
public class LongWorkScope private constructor(private val graph: LongWorkGraph, startImmediately: Boolean) {
    public constructor(
        userScopeProvider: () -> CoroutineScope,
        isSlowSyncOngoingFlowProvider: () -> Flow<Boolean>,
    ) : this(createLongWorkGraph(LongWorkInputs(userScopeProvider, isSlowSyncOngoingFlowProvider)), true)

    public val observeWorkStatus: ObserveWorkStatusUseCase = graph.observeWorkStatus
    public val observeNewWorks: ObserveNewWorkUseCase = graph.observeNewWorks

    init {
        if (startImmediately) graph.initialSyncWorkMonitor.startMonitoring()
    }

    /** Start only after the account owner is registered; repeated startup is harmless. */
    @InternalKaliumApi
    public fun startInitialSyncMonitoring(): Unit = graph.initialSyncWorkMonitor.startMonitoring()

    public companion object {
        /** Retain the original repository/use-case owner without launching work during account construction. */
        @InternalKaliumApi
        public fun prepareForRuntime(
            userScopeProvider: () -> CoroutineScope,
            isSlowSyncOngoingFlowProvider: () -> Flow<Boolean>,
        ): LongWorkScope = LongWorkScope(
            createLongWorkGraph(LongWorkInputs(userScopeProvider, isSlowSyncOngoingFlowProvider)),
            false,
        )
    }
}
