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

package com.wire.kalium.logic.data.auth

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.session.token.AccessToken
import com.wire.kalium.logic.data.session.token.RefreshToken

/**
 * Holds information about the user ID, and the associated user id.
 */
public data class AccountTokens(
    val userId: UserId,
    val accessToken: AccessToken,
    val refreshToken: RefreshToken,
    val cookieLabel: String?
) {
    public constructor(
        userId: UserId,
        accessToken: String,
        refreshToken: String,
        tokenType: String,
        cookieLabel: String?
    ) : this(userId, AccessToken(accessToken, tokenType), RefreshToken(refreshToken), cookieLabel)

    val tokenType: String
        get() = accessToken.tokenType

}
