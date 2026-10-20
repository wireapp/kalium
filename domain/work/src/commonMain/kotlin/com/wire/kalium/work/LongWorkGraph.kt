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

import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** One existing LongWorkScope owner; never one repository per observation or consumer. */
internal abstract class LongWorkLifetime private constructor()

/** Raw function values retain the external owner and live flow factory; Metro does not memoize their results. */
internal class LongWorkInputs(
    val userScopeProvider: () -> CoroutineScope,
    val isSlowSyncOngoingFlowProvider: () -> Flow<Boolean>,
)

internal interface LongWorkGraph {
    val observeWorkStatus: ObserveWorkStatusUseCase
    val observeNewWorks: ObserveNewWorkUseCase
    val initialSyncWorkMonitor: InitialSyncWorkMonitor

    @Provides
    @SingleIn(LongWorkLifetime::class)
    fun provideRepository(): WorkRepository = InMemoryWorkRepository()

    @Provides
    @SingleIn(LongWorkLifetime::class)
    fun provideObserveWorkStatus(repository: WorkRepository): ObserveWorkStatusUseCase = ObserveWorkStatusUseCase(repository)

    @Provides
    @SingleIn(LongWorkLifetime::class)
    fun provideObserveNewWorks(repository: WorkRepository): ObserveNewWorkUseCase = ObserveNewWorkUseCase(repository)

    @Provides
    @SingleIn(LongWorkLifetime::class)
    fun provideInitialSyncWorkMonitor(inputs: LongWorkInputs, repository: WorkRepository): InitialSyncWorkMonitor =
        InitialSyncWorkMonitor(inputs.userScopeProvider(), inputs.isSlowSyncOngoingFlowProvider(), repository)
}

internal expect fun createLongWorkGraph(inputs: LongWorkInputs): LongWorkGraph
