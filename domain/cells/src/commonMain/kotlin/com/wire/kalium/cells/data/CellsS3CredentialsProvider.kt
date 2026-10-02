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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides the credentials used to sign S3 requests.
 *
 * The access token of the current session is reused for every request, so a multipart upload
 * signs all of its requests with the same token instead of refreshing it for every part.
 */
internal interface S3CredentialsProvider {

    /**
     * Credentials based on the access token of the current session.
     */
    suspend fun credentials(): S3Credentials

    /**
     * Credentials to use after [rejectedAccessKeyId] was rejected with 401.
     *
     * The token is refreshed only once when several concurrent requests are rejected with the same
     * token - callers arriving after the refresh reuse the token it produced.
     */
    suspend fun refreshedCredentials(rejectedAccessKeyId: String): S3Credentials
}

internal class CellsS3CredentialsProvider(
    private val cellsCredentials: Deferred<CellsCredentials?>,
    private val sessionManager: SessionManager,
    private val accessTokenApi: AccessTokenApi,
) : S3CredentialsProvider {

    private val refreshMutex = Mutex()

    override suspend fun credentials(): S3Credentials = s3Credentials(
        accessToken = currentAccessToken() ?: refreshMutex.withLock { currentAccessToken() ?: refreshAccessToken() }
    )

    override suspend fun refreshedCredentials(rejectedAccessKeyId: String): S3Credentials = refreshMutex.withLock {
        // Another request may have already refreshed the rejected token while this one was waiting for the lock.
        val currentAccessToken = currentAccessToken()?.takeIf { it != rejectedAccessKeyId }
        s3Credentials(accessToken = currentAccessToken ?: refreshAccessToken())
    }

    private suspend fun s3Credentials(accessToken: String) = S3Credentials(
        accessKeyId = accessToken,
        secretAccessKey = cellsCredentials.awaitOrThrow().gatewaySecret,
    )

    private suspend fun currentAccessToken(): String? =
        sessionManager.session()?.accessToken?.takeIf { it.isNotEmpty() }

    private suspend fun refreshAccessToken(): String =
        sessionManager.updateToken(accessTokenApi, sessionManager.session()?.refreshToken).accessToken
}
