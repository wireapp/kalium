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

public class SessionScope internal constructor(
    private val sessionRepository: SessionRepository
) {
    public val allSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    public val allSessionsFlow: ObserveSessionsUseCase get() = ObserveSessionsUseCase(sessionRepository)
    public val currentSession: CurrentSessionUseCase get() = CurrentSessionUseCase(sessionRepository)
    public val currentSessionFlow: CurrentSessionFlowUseCase get() = CurrentSessionFlowUseCase(sessionRepository)
    public val updateCurrentSession: UpdateCurrentSessionUseCase get() = UpdateCurrentSessionUseCase(sessionRepository)
}
