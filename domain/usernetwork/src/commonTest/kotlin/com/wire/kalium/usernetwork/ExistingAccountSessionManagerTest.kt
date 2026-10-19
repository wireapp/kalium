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

package com.wire.kalium.usernetwork

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.api.model.QualifiedID
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ExistingAccountSessionManagerTest {
    @Test
    fun rotatedTokensArePersistedWithTheExistingCookieLabelBeforeReturning() = runTest {
        val storage = Tokens()
        val api = RefreshApi { success("new-refresh") }
        val manager = manager(storage)
        val result = manager.updateToken(api, "old-refresh")
        assertEquals("new-access", result.accessToken)
        assertEquals("new-refresh", result.refreshToken)
        assertEquals("login-cookie", result.cookieLabel)
        assertEquals("new-access", storage.token?.accessToken)
        assertEquals(listOf<Pair<String, String?>>("old-refresh" to "abc123"), api.requests)
    }

    @Test
    fun refreshWithoutANewCookiePreservesTheCurrentRefreshToken() = runTest {
        val storage = Tokens()
        val result = manager(storage).updateToken(RefreshApi { success(null) }, "old-refresh")
        assertEquals("old-refresh", result.refreshToken)
        assertEquals("old-refresh", storage.token?.refreshToken)
    }

    @Test
    fun persistenceFailureCannotReturnNewlyIssuedCredentials() = runTest {
        val storage = Tokens().apply { failWrite = true }
        val manager = manager(storage)
        assertFailsWith<FailureToRefreshTokenException> {
            manager.updateToken(RefreshApi { success("new-refresh") }, "old-refresh")
        }
        assertEquals("old-access", manager.session().accessToken)
    }

    @Test
    fun refreshCancellationPropagatesWithoutPersistingTokens() = runTest {
        val storage = Tokens()
        val manager = manager(storage)
        assertFailsWith<CancellationException> {
            manager.updateToken(RefreshApi { throw CancellationException("cancelled request") }, "old-refresh")
        }
        assertEquals(0, storage.writes)
    }

    @Test
    fun missingOrMismatchedStoredIdentityCannotConstructASession() {
        assertFailsWith<IllegalStateException> { manager(Tokens().apply { token = null }) }
        assertFailsWith<IllegalStateException> {
            manager(Tokens().apply { token = token!!.copy(userId = UserIDEntity("someone-else", "wire.test")) })
        }
    }

    @Test
    fun anAlreadyRotatedTokenIsReusedWithoutAnotherRefreshRequest() = runTest {
        val storage = Tokens()
        val manager = manager(storage)
        storage.token = storage.token!!.copy(accessToken = "already-refreshed", refreshToken = "new-refresh")
        val result = manager.updateToken(RefreshApi { error("Unexpected second refresh") }, "old-refresh")
        assertEquals("already-refreshed", result.accessToken)
        assertEquals(0, storage.writes)
    }

    @Test
    fun authenticatedContainerRetriesWithThePersistedRefreshedBearerToken() = runTest {
        val storage = Tokens()
        val authorization = mutableListOf<String?>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/access")) {
                respond(
                    """{"user":"self","access_token":"new-access","expires_in":3600,"token_type":"Bearer"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("zuid=new-refresh"))
                )
            } else {
                authorization += request.headers[HttpHeaders.Authorization]
                if (authorization.size == 1) {
                    respond(
                        """{"code":401,"label":"unauthorized","message":"expired"}""",
                        HttpStatusCode.Unauthorized,
                        headersOf(HttpHeaders.WWWAuthenticate, "Bearer")
                    )
                } else respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val container = AuthenticatedNetworkContainer.create(
            manager(storage), null, QualifiedID("self", "wire.test"), "prepared-test", emptyMap(), engine, null, kaliumLogger
        )
        try {
            assertIs<NetworkResponse.Success<*>>(container.clientApi.fetchSelfUserClient())
            assertEquals(listOf<String?>("Bearer old-access", "Bearer new-access"), authorization)
            assertEquals("new-refresh", storage.token?.refreshToken)
        } finally {
            container.close()
            engine.close()
        }
    }

    private fun manager(storage: Tokens) = ExistingAccountSessionManager(
        UserId("self", "wire.test"), ClientId("abc123"),
        ServerConfig("test", ServerConfig.DUMMY, ServerConfig.MetaData(false, CommonApiVersionType.Valid(8), "wire.test")),
        null, storage
    )

    private fun success(refresh: String?) = NetworkResponse.Success(
        AccessTokenDTO("self", "new-access", 3600, "Bearer") to refresh?.let(::RefreshTokenDTO), emptyMap(), 200
    )

    private class RefreshApi(
        private val response: suspend () -> NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>>,
    ) : AccessTokenApi {
        val requests = mutableListOf<Pair<String, String?>>()
        override suspend fun getToken(refreshToken: String, clientId: String?): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>> {
            requests += refreshToken to clientId
            return response()
        }
    }

    private class Tokens : AuthTokenStorage {
        var token: AuthTokenEntity? = AuthTokenEntity(UserIDEntity("self", "wire.test"), "old-access", "old-refresh", "Bearer", "login-cookie")
        var failWrite = false
        var writes = 0
        override fun getToken(userId: UserIDEntity): AuthTokenEntity? = token
        override fun proxyCredentials(userId: UserIDEntity): ProxyCredentialsEntity? = null
        override fun updateToken(userId: UserIDEntity, accessToken: String, tokenType: String, refreshToken: String?): AuthTokenEntity {
            if (failWrite) error("Keychain write failed")
            writes++
            return checkNotNull(token).copy(accessToken = accessToken, tokenType = tokenType, refreshToken = refreshToken ?: token!!.refreshToken)
                .also { token = it }
        }
        override fun addOrReplace(authTokenEntity: AuthTokenEntity, proxyCredentialsEntity: ProxyCredentialsEntity?) {
            error("Prepared networking must never create credentials")
        }
        override fun deleteToken(userId: UserIDEntity) {
            error("Prepared networking must never log out the app")
        }
    }
}
