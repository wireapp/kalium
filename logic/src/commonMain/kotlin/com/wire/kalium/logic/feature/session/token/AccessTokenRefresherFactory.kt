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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.session.token

import com.wire.kalium.logic.data.session.token.AccessTokenRepositoryImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.persistence.client.AuthTokenStorage

/**
 * Represents a factory for creating instances of [AccessTokenRefresher].
 * Allows taking a dynamic [AccessTokenApi] for its construction.
 */
internal interface AccessTokenRefresherFactory {
    fun create(accessTokenApi: AccessTokenApi): AccessTokenRefresher
}

internal class AccessTokenRefresherFactoryImpl(
    private val userId: UserId,
    private val tokenStorage: AuthTokenStorage
) : AccessTokenRefresherFactory {
    override fun create(accessTokenApi: AccessTokenApi): AccessTokenRefresher {
        return AccessTokenRefresherImpl(
            repository = AccessTokenRepositoryImpl(
                userId = userId,
                accessTokenApi = accessTokenApi,
                authTokenStorage = tokenStorage
            )
        )
    }
}
