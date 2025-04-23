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
package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SessionManagerTest {

    @Test
    fun givenFailureOnRefresh_whenRefreshingToken_thenShouldThrowException() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, sessionManager) = arrange {
            withTokenRefresherResult(Either.Left(failure))
        }

        assertFailsWith<FailureToRefreshTokenException> {
            sessionManager.updateToken(arrangement.accessTokenApi, "egal", "egal")
        }
    }

    @Test
    fun givenInitialSession_whenFetchingSession_thenSessionShouldBeReturnedProperly() = runTest {
        val expectedData = AuthTokenEntity(
            userId = UserIDEntity("potato", "potahto"),
            accessToken = "aToken",
            refreshToken = "rToken",
            tokenType = "tType",
            cookieLabel = null
        )
        val (_, sessionManager) = arrange {
            withCurrentTokenResult(expectedData)
        }

        val result = sessionManager.session()
        assertNotNull(result)
        assertEquals(expectedData.userId.value, result.userId.value)
        assertEquals(expectedData.userId.domain, result.userId.domain)
        assertEquals(expectedData.accessToken, result.accessToken)
        assertEquals(expectedData.refreshToken, result.refreshToken)
        assertEquals(expectedData.tokenType, result.tokenType)
        assertEquals(expectedData.cookieLabel, result.cookieLabel)
    }

    @Test
    fun givenSuccess_whenUpdatingToken_thenItShouldCallTokenRefresherCorrectly() = runTest {
        val (arrangement, sessionManager) = arrange {
            withTokenRefresherResult(Either.Right(TEST_ACCOUNT_TOKENS))
        }

        val accessToken = "egal"
        val refreshToken = "refreshToken"
        sessionManager.updateToken(arrangement.accessTokenApi, accessToken, refreshToken)

        coVerify {

            arrangement.accessTokenRefresher.refreshTokenAndPersistSession(eq(accessToken), eq(refreshToken))

        }
    }

    @Test
    fun givenSessionWasUpdated_whenGettingSession_thenItShouldBeUpdatedAsWell() = runTest {
        var counter = 0
        val originalTokens = AuthTokenEntity(
            userId = UserIDEntity("potato", "potahto"),
            accessToken = "aToken",
            refreshToken = "rToken",
            tokenType = "tType",
            cookieLabel = "cLabel"
        )
        val updatedTokens = AuthTokenEntity(
            userId = UserIDEntity("updated userId", "updated userDomain"),
            accessToken = "a completely different token",
            refreshToken = "a completely different refresh token",
            tokenType = "updated tType",
            cookieLabel = "updated cLabel"
        )
        val (_, sessionManager) = arrange {
            withCurrentTokenReturning {
                counter++
                if (counter == 1) {
                    originalTokens
                } else {
                    updatedTokens
                }
            }
        }

        val firstResult = sessionManager.session()!!
        val secondResult = sessionManager.session()!!

        assertEquals(originalTokens.accessToken, firstResult.accessToken)
        assertEquals(updatedTokens.accessToken, secondResult.accessToken)
        assertEquals(updatedTokens.refreshToken, secondResult.refreshToken)
        assertEquals(updatedTokens.userId.value, secondResult.userId.value)
        assertEquals(updatedTokens.userId.domain, secondResult.userId.domain)
        assertEquals(updatedTokens.tokenType, secondResult.tokenType)
        assertEquals(updatedTokens.cookieLabel, secondResult.cookieLabel)
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) {
        private val sessionRepository = mock(SessionRepository::class)

        // Unused, but necessary when updating tokens
                val accessTokenApi = mock(AccessTokenApi::class)
        val accessTokenRefresher = mock(AccessTokenRefresher::class)
        private val accessTokenRefresherFactory = object : AccessTokenRefresherFactory {
            override fun create(accessTokenApi: AccessTokenApi): AccessTokenRefresher {
                return accessTokenRefresher
            }
        }
        private val userId = TestUser.USER_ID
        private val tokenStorage = mock(AuthTokenStorage::class)

        private val logout = { _: LogoutReason -> }
        private val serverConfigMapper = mock(ServerConfigMapper::class)

        private val sessionMapper = MapperProvider.sessionMapper()

        suspend fun arrange(): Pair<Arrangement, SessionManager> = run {
            configure()
            this@Arrangement to SessionManagerImpl(
                sessionRepository = sessionRepository,
                accessTokenRefresherFactory = accessTokenRefresherFactory,
                userId = userId,
                tokenStorage = tokenStorage,
                logout = logout,
                serverConfigMapper = serverConfigMapper,
                sessionMapper = sessionMapper,
                coroutineContext = EmptyCoroutineContext
            )
        }

        suspend fun withTokenRefresherResult(result: Either<CoreFailure, AccountTokens>) = apply {
            coEvery {
                accessTokenRefresher.refreshTokenAndPersistSession(any(), any())
            }.returns(result)
        }

        fun withCurrentTokenResult(result: AuthTokenEntity) = apply {
            withCurrentTokenReturning { result }
        }

        fun withCurrentTokenReturning(block: (args: Array<Any?>) -> AuthTokenEntity) = apply {
            every {
                tokenStorage.getToken(any())
            }.invokes(block)
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
        val TEST_ACCOUNT_TOKENS = AccountTokens(
            userId = TestUser.USER_ID,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "type",
            cookieLabel = "cookie-label"
        )
        val TEST_SESSION_DTO = SessionDTO(
            userId = QualifiedID(TEST_ACCOUNT_TOKENS.userId.value, TEST_ACCOUNT_TOKENS.userId.domain),
            tokenType = TEST_ACCOUNT_TOKENS.accessToken.tokenType,
            accessToken = TEST_ACCOUNT_TOKENS.accessToken.value,
            refreshToken = TEST_ACCOUNT_TOKENS.refreshToken.value,
            cookieLabel = TEST_ACCOUNT_TOKENS.cookieLabel
        )
    }
}
