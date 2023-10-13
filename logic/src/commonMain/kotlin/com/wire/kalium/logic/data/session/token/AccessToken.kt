/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.session.token

import kotlin.jvm.JvmInline

internal sealed interface AccessTokenRefreshResult {
    val accessToken: AccessToken

    /**
     * Represents a result of an access token refresh, where the refresh token remains the same.
     *
     * @param accessToken The new access token.
     */
    data class SameRefreshToken(
        override val accessToken: AccessToken
    ) : AccessTokenRefreshResult

    /**
     * Represents a new refresh token along with the corresponding access token.
     *
     * @property accessToken The new access token.
     * @property refreshToken The new refresh token.
     */
    data class NewRefreshToken(
        override val accessToken: AccessToken,
        val refreshToken: RefreshToken
    ) : AccessTokenRefreshResult
}

/**
 * Represents an access token, which is used for authentication and authorization purposes.
 *
 * @property value The value of the access token.
 * @property tokenType The type of the access token. _e.g._ "Bearer"
 */
data class AccessToken(
    val value: String,
    val tokenType: String
)

@JvmInline
value class RefreshToken(val value: String)
