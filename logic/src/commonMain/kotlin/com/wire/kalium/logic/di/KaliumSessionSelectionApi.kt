/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.session.CurrentSessionFlowUseCase
import com.wire.kalium.logic.feature.session.CurrentSessionUseCase
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.ObserveSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionGraph
import com.wire.kalium.logic.feature.session.UpdateCurrentSessionUseCase

/** Individual bindings preserve their existing owner; this API does not create a consumer. */
public interface KaliumSessionSelectionApi {
    public val currentSession: CurrentSessionUseCase
    public val updateCurrentSession: UpdateCurrentSessionUseCase
    public val allSessions: GetSessionsUseCase
    public val allSessionsFlow: ObserveSessionsUseCase
    public val currentSessionFlow: CurrentSessionFlowUseCase

    /** Every call creates a fresh graph with the original per-facade captures and caches. */
    public fun interface Factory {
        public fun create(): KaliumSessionSelectionApi
    }
}

internal fun createSessionSelectionApi(
    factory: SessionGraph.Factory,
    repository: SessionRepository,
): KaliumSessionSelectionApi = factory.create(repository)
