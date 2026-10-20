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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

internal interface SessionApi {
    val allSessions: GetSessionsUseCase
    val allSessionsIncludingInvalid: GetAllSessionsUseCase
    val allSessionsFlow: ObserveSessionsUseCase
    val currentSession: CurrentSessionUseCase
    val currentSessionFlow: CurrentSessionFlowUseCase
    val updateCurrentSession: UpdateCurrentSessionUseCase
}

/**
 * A new graph for every GlobalKaliumScope.session access. The global owner supplies the exact
 * repository resolved for that access; every use case in this graph keeps that same capture.
 * Neither the graph nor its use cases are cached in the global or user lifetime.
 */
@GraphExtension
internal interface SessionGraph : SessionApi {
    @Provides
    fun provideAllSessions(repository: SessionRepository): GetSessionsUseCase = GetSessionsUseCase(repository)

    @Provides
    fun provideAllSessionsIncludingInvalid(repository: SessionRepository): GetAllSessionsUseCase = GetAllSessionsUseCase(repository)

    @Provides
    fun provideAllSessionsFlow(repository: SessionRepository): ObserveSessionsUseCase = ObserveSessionsUseCase(repository)

    @Provides
    fun provideCurrentSession(repository: SessionRepository): CurrentSessionUseCase = CurrentSessionUseCase(repository)

    @Provides
    fun provideCurrentSessionFlow(repository: SessionRepository): CurrentSessionFlowUseCase = CurrentSessionFlowUseCase(repository)

    @Provides
    fun provideUpdateCurrentSession(repository: SessionRepository): UpdateCurrentSessionUseCase = UpdateCurrentSessionUseCase(repository)

    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides repository: SessionRepository): SessionGraph
    }
}
