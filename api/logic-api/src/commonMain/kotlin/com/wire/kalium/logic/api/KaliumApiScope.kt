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
package com.wire.kalium.logic.api

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.api.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.api.feature.user.GetSelfUserUseCaseImpl
import com.wire.kalium.logic.api.feature.user.ObserveSelfUserUseCase
import com.wire.kalium.logic.api.feature.user.ObserveSelfUserUseCaseImpl
import com.wire.kalium.logic.data.user.UserId

/**
 * Main entry point for Kalium API.
 * This provides a stable, versioned public API for external consumers.
 */
public class KaliumApiScope internal constructor(
    private val coreLogic: CoreLogic,
    internal val userId: UserId
) {
    // TODO: Add delegating use cases here
    public val observeSelfUser: ObserveSelfUserUseCase by lazy {
        ObserveSelfUserUseCaseImpl(coreLogic.getSessionScope(userId).users.observeSelfUser)
    }

    public val getSelfUser: GetSelfUserUseCase by lazy {
        GetSelfUserUseCaseImpl(coreLogic.getSessionScope(userId).users.getSelfUser)
    }
}
