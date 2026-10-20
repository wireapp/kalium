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

/** Compatibility facade over one fresh session graph and its captured repository. */
public class SessionScope internal constructor(private val api: SessionApi) {
    public val allSessions: GetSessionsUseCase get() = api.allSessions
    public val allSessionsIncludingInvalid: GetAllSessionsUseCase get() = api.allSessionsIncludingInvalid
    public val allSessionsFlow: ObserveSessionsUseCase get() = api.allSessionsFlow
    public val currentSession: CurrentSessionUseCase get() = api.currentSession
    public val currentSessionFlow: CurrentSessionFlowUseCase get() = api.currentSessionFlow
    public val updateCurrentSession: UpdateCurrentSessionUseCase get() = api.updateCurrentSession
}
