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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.session.token.AccessToken
import com.wire.kalium.logic.data.session.token.AccessTokenRefreshResult
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.session.token.RefreshToken
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessTokenRefresherTest {

    @Test
    fun givenRefreshFails_whenRefreshing_thenShouldPropagateFailureAndNotPersist(): TestResult = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, accessTokenRefresher) = arrange {
            withRefreshTokenReturning(Either.Left(failure))
        }

        accessTokenRefresher.refreshTokenAndPersistSession("egal")
            .shouldFail {
                assertEquals(failure, it)
            }
        coVerify {
            arrangement.repository.persistTokens(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenPersistFails_whenRefreshing_thenShouldPropagateFailure(): TestResult = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, accessTokenRefresher) = arrange {
            withRefreshTokenReturning(
                Either.Right(
                    AccessTokenRefreshResult(
                        AccessToken("access", "refresh"),
                        RefreshToken("hey")
                    )
                )
            )
            withPersistReturning(Either.Left(failure))
        }

        accessTokenRefresher.refreshTokenAndPersistSession("egal")
            .shouldFail {
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenSuccessfulRefresh_whenRefreshing_thenShouldPersistResultCorrectly(): TestResult = runTest {
        val expected = AccountTokens(userId = TestUser.USER_ID, TEST_REFRESH_RESULT.accessToken, TEST_REFRESH_RESULT.refreshToken, null)

        val (arrangement, accessTokenRefresher) = arrange {
            withRefreshTokenReturning(Either.Right(TEST_REFRESH_RESULT))
            withPersistReturning(Either.Right(expected))
        }

        accessTokenRefresher.refreshTokenAndPersistSession("egal")
        coVerify {
            arrangement.repository.persistTokens(eq(TEST_REFRESH_RESULT.accessToken), eq(TEST_REFRESH_RESULT.refreshToken))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEverythingSucceeds_whenRefreshing_thenShouldPropagateSuccess(): TestResult = runTest {

        val expected = AccountTokens(userId = TestUser.USER_ID, TEST_REFRESH_RESULT.accessToken, TEST_REFRESH_RESULT.refreshToken, null)
        val (_, accessTokenRefresher) = arrange {
            withRefreshTokenReturning(Either.Right(TEST_REFRESH_RESULT))
            withPersistReturning(Either.Right(expected))
        }

        accessTokenRefresher.refreshTokenAndPersistSession("egal").shouldSucceed { }
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) {

        val repository = mock(AccessTokenRepository::class)

        suspend fun arrange(): Pair<Arrangement, AccessTokenRefresher> = run {
            configure()
            this@Arrangement to AccessTokenRefresherImpl(repository)
        }

        suspend fun withRefreshTokenReturning(result: Either<NetworkFailure, AccessTokenRefreshResult>) {
            coEvery {
                repository.getNewAccessToken(any(), any())
            }.returns(result)
        }

        suspend fun withPersistReturning(result: Either<StorageFailure, AccountTokens>) {
            coEvery {
                repository.persistTokens(any(), any())
            }.returns(result)
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()

        val TEST_REFRESH_RESULT = AccessTokenRefreshResult(
            AccessToken("access", "refresh"),
            RefreshToken("hey")
        )
    }
}
