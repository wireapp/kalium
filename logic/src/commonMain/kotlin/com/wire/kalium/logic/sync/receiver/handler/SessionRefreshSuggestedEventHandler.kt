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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import io.mockative.Mockable

@Mockable
internal interface SessionRefreshSuggestedEventHandler {
    suspend fun handle(event: Event.User.SessionRefreshSuggested): Either<CoreFailure, Unit>
}

internal class SessionRefreshSuggestedEventHandlerImpl(
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    private val sessionManager: SessionManager
) : SessionRefreshSuggestedEventHandler {
    override suspend fun handle(event: Event.User.SessionRefreshSuggested): Either<CoreFailure, Unit> {
        val refreshToken = sessionManager.session()?.refreshToken
            ?: return Either.Left(CoreFailure.Unknown(IllegalStateException("Missing refresh token")))

        return try {
            sessionManager.updateToken(authenticatedNetworkContainer.accessTokenApi, refreshToken)
            authenticatedNetworkContainer.clearCachedToken()
            Either.Right(Unit)
        } catch (exception: FailureToRefreshTokenException) {
            Either.Left(CoreFailure.Unknown(exception))
        }
    }
}
