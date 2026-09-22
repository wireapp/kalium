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

package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.network.api.model.UserId as UserIdDTO
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.ProxyCredentialsEntity

fun AccountTokens.toSessionDTO(): SessionDTO = with(this) {
    SessionDTO(
        userId = UserIdDTO(userId.value, userId.domain),
        tokenType = tokenType,
        accessToken = accessToken.value,
        refreshToken = refreshToken.value,
        cookieLabel = cookieLabel
    )
}

fun AuthTokenEntity.toSessionDTO(): SessionDTO = with(this) {
    SessionDTO(
        userId = UserIdDTO(userId.value, userId.domain),
        tokenType = tokenType,
        accessToken = accessToken,
        refreshToken = refreshToken,
        cookieLabel = cookieLabel
    )
}

fun AuthTokenEntity.toAccountTokens(): AccountTokens = AccountTokens(
    userId = UserId(userId.value, userId.domain),
    accessToken = accessToken,
    refreshToken = refreshToken,
    tokenType = tokenType,
    cookieLabel = cookieLabel
)

fun ProxyCredentialsEntity.toProxyCredentialsDTO(): ProxyCredentialsDTO = ProxyCredentialsDTO(username, password)
