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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SessionRefreshSuggestedEventHandlerTest {

    @Test
    fun givenRefreshToken_whenHandling_thenSessionIsRefreshedAndCacheCleared() = runTest {
        val (arrangement, handler) = Arrangement()
            .withDefaults()
            .arrange()

        val result = handler.handle(TestEvent.sessionRefreshSuggested())

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionManager.updateToken(eq(arrangement.accessTokenApi), eq(TEST_SESSION.refreshToken))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.authenticatedNetworkContainer.clearCachedToken()
        }
    }

    @Test
    fun givenMissingRefreshToken_whenHandling_thenFailureIsReturned() = runTest {
        val (_, handler) = Arrangement()
            .withDefaults()
            .withSession(null)
            .arrange()

        val result = handler.handle(TestEvent.sessionRefreshSuggested())

        assertIs<Either.Left<CoreFailure.Unknown>>(result)
    }

    @Test
    fun givenRefreshFailure_whenHandling_thenFailureIsReturned() = runTest {
        val (_, handler) = Arrangement()
            .withDefaults()
            .withUpdateTokenException(FailureToRefreshTokenException("refresh failed"))
            .arrange()

        val result = handler.handle(TestEvent.sessionRefreshSuggested())

        assertIs<Either.Left<CoreFailure.Unknown>>(result)
    }

    private class Arrangement {
        val accessTokenApi: AccessTokenApi = mock()
        val sessionManager: SessionManager = mock()
        val authenticatedNetworkContainer: AuthenticatedNetworkContainer = mock()

        suspend fun arrange(): Pair<Arrangement, SessionRefreshSuggestedEventHandler> =
            this to SessionRefreshSuggestedEventHandlerImpl(
                authenticatedNetworkContainer,
                sessionManager
            )

        suspend fun withDefaults() = apply {
            withSession(TEST_SESSION)
            withUpdateTokenSuccess()
            withClearCachedToken()
            withAccessTokenApi()
        }

        suspend fun withSession(session: SessionDTO?) = apply {
            everySuspend {
                sessionManager.session()
            } returns session
        }

        suspend fun withUpdateTokenSuccess() = apply {
            everySuspend {
                sessionManager.updateToken(any(), any())
            } returns TEST_SESSION
        }

        suspend fun withUpdateTokenException(exception: FailureToRefreshTokenException) = apply {
            everySuspend {
                sessionManager.updateToken(any(), any())
            }.throws(exception)
        }

        suspend fun withClearCachedToken() = apply {
            everySuspend {
                authenticatedNetworkContainer.clearCachedToken()
            } returns Unit
        }

        fun withAccessTokenApi() = apply {
            every {
                authenticatedNetworkContainer.accessTokenApi
            } returns accessTokenApi
        }
    }

    private companion object {
        val TEST_SESSION = SessionDTO(
            userId = TestUser.USER_ID.toApi(),
            tokenType = "Bearer",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            cookieLabel = "cookie-label"
        )
    }
}
