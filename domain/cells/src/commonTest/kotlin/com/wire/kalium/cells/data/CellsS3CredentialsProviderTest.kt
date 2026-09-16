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
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CellsS3CredentialsProviderTest {

    @Test
    fun givenSessionWithAccessToken_whenRequestingCredentialsRepeatedly_thenTokenIsReusedWithoutRefreshing() = runTest {
        val sessionManager = FakeSessionManager()
        val provider = createProvider(sessionManager)

        val credentials = List(REQUEST_COUNT) { provider.credentials() }

        assertEquals(0, sessionManager.refreshCount)
        credentials.forEach { assertEquals(S3Credentials(INITIAL_ACCESS_TOKEN, GATEWAY_SECRET), it) }
    }

    @Test
    fun givenSessionWithoutAccessToken_whenRequestingCredentials_thenTokenIsRefreshedOnce() = runTest {
        val sessionManager = FakeSessionManager(initialAccessToken = "")
        val provider = createProvider(sessionManager)

        val credentials = provider.credentials()

        assertEquals(1, sessionManager.refreshCount)
        assertEquals(S3Credentials("$REFRESHED_ACCESS_TOKEN_PREFIX-1", GATEWAY_SECRET), credentials)
    }

    @Test
    fun givenRejectedToken_whenRequestingRefreshedCredentials_thenTokenIsRefreshedOnce() = runTest {
        val sessionManager = FakeSessionManager()
        val provider = createProvider(sessionManager)

        val credentials = provider.refreshedCredentials(INITIAL_ACCESS_TOKEN)

        assertEquals(1, sessionManager.refreshCount)
        assertEquals(S3Credentials("$REFRESHED_ACCESS_TOKEN_PREFIX-1", GATEWAY_SECRET), credentials)
        assertEquals(listOf<String?>(INITIAL_REFRESH_TOKEN), sessionManager.usedRefreshTokens.toList())
    }

    @Test
    fun givenTokenAlreadyRotated_whenRequestingRefreshedCredentials_thenCurrentTokenIsReused() = runTest {
        val sessionManager = FakeSessionManager()
        val provider = createProvider(sessionManager)

        val credentials = provider.refreshedCredentials("some-older-token")

        assertEquals(0, sessionManager.refreshCount)
        assertEquals(S3Credentials(INITIAL_ACCESS_TOKEN, GATEWAY_SECRET), credentials)
    }

    @Test
    fun givenConcurrentRequestsRejectedWithSameToken_whenRefreshing_thenTokenIsRefreshedOnlyOnce() = runTest {
        val sessionManager = FakeSessionManager()
        val provider = createProvider(sessionManager)
        val firstRefreshStarted = CompletableDeferred<Unit>()
        val firstRefreshRelease = CompletableDeferred<Unit>()
        sessionManager.onRefresh = {
            firstRefreshStarted.complete(Unit)
            firstRefreshRelease.await()
        }

        val first = async { provider.refreshedCredentials(INITIAL_ACCESS_TOKEN) }
        firstRefreshStarted.await()
        val second = async { provider.refreshedCredentials(INITIAL_ACCESS_TOKEN) }
        runCurrent()
        firstRefreshRelease.complete(Unit)

        assertEquals(S3Credentials("$REFRESHED_ACCESS_TOKEN_PREFIX-1", GATEWAY_SECRET), first.await())
        assertEquals(first.await(), second.await())
        assertEquals(1, sessionManager.refreshCount)
    }

    @Test
    fun givenMissingCellsCredentials_whenRequestingCredentials_thenFails() = runTest {
        val missingCredentials = CompletableDeferred<CellsCredentials?>()
        missingCredentials.complete(null)
        val provider = CellsS3CredentialsProvider(
            cellsCredentials = missingCredentials,
            sessionManager = FakeSessionManager(),
            accessTokenApi = FakeAccessTokenApi,
        )

        assertFailsWith<CellsCredentialsUnavailableException> { provider.credentials() }
    }

    private fun createProvider(sessionManager: SessionManager) = CellsS3CredentialsProvider(
        cellsCredentials = CompletableDeferred(CellsCredentials(TEST_ENDPOINT, GATEWAY_SECRET)),
        sessionManager = sessionManager,
        accessTokenApi = FakeAccessTokenApi,
    )

    private class FakeSessionManager(
        initialAccessToken: String = INITIAL_ACCESS_TOKEN,
    ) : SessionManager {

        var refreshCount = 0
            private set
        val usedRefreshTokens = mutableListOf<String?>()
        var onRefresh: suspend () -> Unit = {}

        private var accessToken = initialAccessToken

        override suspend fun session(): SessionDTO = SessionDTO(
            userId = QualifiedID("user-id", "domain"),
            tokenType = "Bearer",
            accessToken = accessToken,
            refreshToken = INITIAL_REFRESH_TOKEN,
            cookieLabel = null,
        )

        override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?): SessionDTO {
            refreshCount++
            usedRefreshTokens += oldRefreshToken
            onRefresh()
            accessToken = "$REFRESHED_ACCESS_TOKEN_PREFIX-$refreshCount"
            return session()
        }

        override fun serverConfig(): ServerConfigDTO = error("Not used by the credentials provider")

        override fun nomadServiceUrl(): String? = null

        override fun proxyCredentials(): ProxyCredentialsDTO? = null
    }

    private object FakeAccessTokenApi : AccessTokenApi {
        override suspend fun getToken(
            refreshToken: String,
            clientId: String?
        ): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>> = error("Not used by the credentials provider")
    }

    private companion object {
        const val REQUEST_COUNT = 5
        const val INITIAL_ACCESS_TOKEN = "access-token"
        const val INITIAL_REFRESH_TOKEN = "refresh-token"
        const val REFRESHED_ACCESS_TOKEN_PREFIX = "refreshed-access-token"
        const val GATEWAY_SECRET = "gatewaysecret"
    }
}
