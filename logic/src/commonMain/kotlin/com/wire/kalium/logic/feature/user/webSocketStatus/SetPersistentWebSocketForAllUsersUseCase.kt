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

package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.session.SessionRepository

/**
 * This use case is responsible for setting the persistent web socket connection status for all users.
 */
public interface SetPersistentWebSocketForAllUsersUseCase {
    /**
     * @param enabled true if the persistent web socket connection should be enabled for all users, false otherwise
     */
    public suspend operator fun invoke(enabled: Boolean): SetAllPersistentWebSocketEnabledResult
}

public sealed class SetAllPersistentWebSocketEnabledResult {
    public data object Success : SetAllPersistentWebSocketEnabledResult()
    public data class Failure(val failure: CoreFailure) : SetAllPersistentWebSocketEnabledResult()
}

internal class SetPersistentWebSocketForAllUsersUseCaseImpl(
    private val sessionRepository: SessionRepository
) : SetPersistentWebSocketForAllUsersUseCase {
    override suspend operator fun invoke(enabled: Boolean): SetAllPersistentWebSocketEnabledResult =
        sessionRepository.setAllPersistentWebSocketEnabled(enabled).fold({
            SetAllPersistentWebSocketEnabledResult.Failure(it)
        }, {
            SetAllPersistentWebSocketEnabledResult.Success
        })
}
