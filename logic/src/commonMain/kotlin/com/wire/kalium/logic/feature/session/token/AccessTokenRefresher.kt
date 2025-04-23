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
package com.wire.kalium.logic.feature.session.token

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import io.mockative.Mockable

@Mockable
internal interface AccessTokenRefresher {
    /**
     * Refreshes the access token using the provided refresh token and persists the session in the repository.
     *
     * @param currentRefreshToken The refresh token to use for obtaining a new access token.
     * @param clientId The optional client ID associated with the new token.
     * @return Either a [CoreFailure] if the operation fails, or the [AccountTokens] with the new access token and refresh token.
     */
    suspend fun refreshTokenAndPersistSession(
        currentRefreshToken: String,
        clientId: String? = null,
    ): Either<CoreFailure, AccountTokens>
}

internal class AccessTokenRefresherImpl(
    private val repository: AccessTokenRepository,
) : AccessTokenRefresher {
    override suspend fun refreshTokenAndPersistSession(
        currentRefreshToken: String,
        clientId: String?
    ): Either<CoreFailure, AccountTokens> {
        return repository.getNewAccessToken(
            refreshToken = currentRefreshToken,
            clientId = clientId
        ).flatMap { result ->
            repository.persistTokens(result.accessToken, result.refreshToken)
        }
    }
}
