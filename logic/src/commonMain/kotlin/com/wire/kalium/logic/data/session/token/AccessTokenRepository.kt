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
package com.wire.kalium.logic.data.session.token

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.persistence.client.AuthTokenStorage

internal interface AccessTokenRepository {
    /**
     * Retrieves a new access token using the provided refresh token and client ID.
     *
     * If provided, the new token will be associated with this client ID.
     * If the client is remotely removed by the user, the tokens will be invalidated.
     * Future refreshes will keep the previously associated client ID.
     * _i.e._ after the first refresh, the client ID doesn't need to be provided anymore.
     *
     * @param refreshToken The refresh token to use for obtaining a new access token.
     * @param clientId The optional client ID.
     * @return Either a [CoreFailure] or the new access token.
     */
    suspend fun getNewAccessToken(
        refreshToken: String,
        clientId: String? = null
    ): Either<NetworkFailure, AccessTokenRefreshResult>

    /**
     * Persists the access token and refresh token in the repository.
     *
     * @param accessToken The access token to persist.
     * @param refreshToken The refresh token to persist.
     * @return Either a [CoreFailure] if the operation fails, or [Unit] if the tokens are successfully persisted.
     */
    suspend fun persistTokens(
        accessToken: AccessToken,
        refreshToken: RefreshToken
    ): Either<CoreFailure, AccountTokens>
}

internal class AccessTokenRepositoryImpl(
    private val userId: UserId,
    private val accessTokenApi: AccessTokenApi,
    private val authTokenStorage: AuthTokenStorage,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) : AccessTokenRepository {
    override suspend fun getNewAccessToken(
        refreshToken: String,
        clientId: String?
    ): Either<NetworkFailure, AccessTokenRefreshResult> = wrapApiRequest {
        accessTokenApi.getToken(refreshToken, clientId)
    }.map { (accessTokenDTO, newRefreshToken) ->
        val token = AccessToken(accessTokenDTO.value, accessTokenDTO.tokenType)
        val resolvedRefreshToken = newRefreshToken?.value ?: refreshToken
        AccessTokenRefreshResult(token, RefreshToken(resolvedRefreshToken))
    }

    override suspend fun persistTokens(
        accessToken: AccessToken,
        refreshToken: RefreshToken
    ): Either<StorageFailure, AccountTokens> = wrapStorageRequest {
        authTokenStorage.updateToken(
            userId.toDao(),
            accessToken.value,
            accessToken.tokenType,
            refreshToken.value
        )
    }.map {
        sessionMapper.toAccountTokens(it)
    }
}
