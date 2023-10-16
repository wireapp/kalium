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
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.AccountTokens
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.AuthTokenStorage
import io.mockative.Mock
import io.mockative.anything
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFailsWith

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

        @Mock
        private val sessionMapper = mock(SessionMapper::class)

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
            given(accessTokenRefresher)
                .suspendFunction(accessTokenRefresher::refreshTokenAndPersistSession)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }
    }

    private companion object {
        fun arrange(configure: Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
