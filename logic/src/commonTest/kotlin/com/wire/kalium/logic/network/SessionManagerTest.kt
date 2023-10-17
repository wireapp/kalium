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
package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AccountTokens
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.given
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
    fun givenInitialSessionIsUpdated_whenFetchingSession_thenSessionShouldBeUpdatedProperly() = runTest {
        val expectedData = TEST_ACCOUNT_TOKENS
        val (arrangement, sessionManager) = arrange {
            withCurrentTokenResult(
                AuthTokenEntity(
                    userId = UserIDEntity("potato", "potahto"),
                    accessToken = "aToken",
                    refreshToken = "rToken",
                    tokenType = "tType",
                    cookieLabel = "cLabel"
                )
            )
            withTokenRefresherResult(Either.Right(expectedData))
        }

        sessionManager.session()
        sessionManager.updateToken(arrangement.accessTokenApi, "egal", "egal")
        val result = sessionManager.session()
        assertNotNull(result)
        assertEquals(expectedData.userId.value, result.userId.value)
        assertEquals(expectedData.userId.domain, result.userId.domain)
        assertEquals(expectedData.accessToken.value, result.accessToken)
        assertEquals(expectedData.refreshToken.value, result.refreshToken)
        assertEquals(expectedData.tokenType, result.tokenType)
        assertEquals(expectedData.cookieLabel, result.cookieLabel)
    }

    @Test
    fun givenTokenWasUpdated_whenGettingSession_thenItShouldBeUpdatedAsWell() = runTest {
        val (arrangement, sessionManager) = arrange {
            withTokenRefresherResult(Either.Right(TEST_ACCOUNT_TOKENS))
        }

        sessionManager.updateToken(arrangement.accessTokenApi, "egal", "egal")

        assertEquals(TEST_SESSION_DTO, sessionManager.session())
    }

    private class Arrangement(private val configure: Arrangement.() -> Unit) {

        @Mock
        private val sessionRepository = mock(SessionRepository::class)

        // Unused, but necessary when updating tokens
        @Mock
        val accessTokenApi = mock(AccessTokenApi::class)

        @Mock
        private val accessTokenRefresher = mock(AccessTokenRefresher::class)
        private val accessTokenRefresherFactory = object : AccessTokenRefresherFactory {
            override fun create(accessTokenApi: AccessTokenApi): AccessTokenRefresher {
                return accessTokenRefresher
            }
        }
        private val userId = TestUser.USER_ID

        @Mock
        private val tokenStorage = mock(AuthTokenStorage::class)

        private val logout = { _: LogoutReason -> }

        @Mock
        private val serverConfigMapper = mock(ServerConfigMapper::class)

        private val sessionMapper = MapperProvider.sessionMapper()

        fun arrange(): Pair<Arrangement, SessionManager> = run {
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

        fun withTokenRefresherResult(result: Either<CoreFailure, AccountTokens>) = apply {
            given(accessTokenRefresher).suspendFunction(accessTokenRefresher::refreshTokenAndPersistSession)
                .whenInvokedWith(anything(), anything()).thenReturn(result)
        }

        fun withCurrentTokenResult(result: AuthTokenEntity) = apply {
            given(tokenStorage)
                .function(tokenStorage::getToken)
                .whenInvokedWith(any())
                .thenReturn(result)
        }
    }

    private companion object {
        fun arrange(configure: Arrangement.() -> Unit) = Arrangement(configure).arrange()
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
