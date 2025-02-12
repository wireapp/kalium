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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.session

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager

/**
 * Upgrade the current login session to be associated with self user's client ID
 */
interface UpgradeCurrentSessionUseCase {
    suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, Unit>
}

internal class UpgradeCurrentSessionUseCaseImpl(
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    private val accessTokenRefresher: AccessTokenRefresher,
    private val sessionManager: SessionManager
) : UpgradeCurrentSessionUseCase {
    override suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, Unit> =
        wrapStorageRequest { sessionManager.session()?.refreshToken }
            .flatMap { currentRefreshToken ->
                accessTokenRefresher.refreshTokenAndPersistSession(currentRefreshToken, clientId.value)
            }.map {
                authenticatedNetworkContainer.clearCachedToken()
            }
}
