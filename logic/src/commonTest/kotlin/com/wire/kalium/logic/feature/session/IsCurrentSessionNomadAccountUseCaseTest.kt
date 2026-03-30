/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.Account
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class IsCurrentSessionNomadAccountUseCaseTest {

    @Test
    fun givenCurrentSessionIsValidWithNomadUrl_thenReturnTrue() = runTest {
        val (_, useCase) = Arrangement()
            .withCurrentSession(Either.Right(AccountInfo.Valid(TEST_USER_ID)))
            .withFullAccountInfo(TEST_USER_ID, Either.Right(testAccount(nomadServiceUrl = "https://nomad.example.com")))
            .arrange()

        assertTrue(useCase())
    }

    @Test
    fun givenCurrentSessionIsValidWithoutNomadUrl_thenReturnFalse() = runTest {
        val (_, useCase) = Arrangement()
            .withCurrentSession(Either.Right(AccountInfo.Valid(TEST_USER_ID)))
            .withFullAccountInfo(TEST_USER_ID, Either.Right(testAccount(nomadServiceUrl = null)))
            .arrange()

        assertFalse(useCase())
    }

    @Test
    fun givenCurrentSessionIsValidWithBlankNomadUrl_thenReturnFalse() = runTest {
        val (_, useCase) = Arrangement()
            .withCurrentSession(Either.Right(AccountInfo.Valid(TEST_USER_ID)))
            .withFullAccountInfo(TEST_USER_ID, Either.Right(testAccount(nomadServiceUrl = "  ")))
            .arrange()

        assertFalse(useCase())
    }

    @Test
    fun givenCurrentSessionIsInvalid_thenReturnFalse() = runTest {
        val (_, useCase) = Arrangement()
            .withCurrentSession(Either.Right(AccountInfo.Invalid(TEST_USER_ID, LogoutReason.SELF_HARD_LOGOUT)))
            .arrange()

        assertFalse(useCase())
    }

    @Test
    fun givenNoCurrentSession_thenReturnFalse() = runTest {
        val (_, useCase) = Arrangement()
            .withCurrentSession(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        assertFalse(useCase())
    }

    @Test
    fun givenFullAccountInfoFails_thenReturnFalse() = runTest {
        val (_, useCase) = Arrangement()
            .withCurrentSession(Either.Right(AccountInfo.Valid(TEST_USER_ID)))
            .withFullAccountInfo(TEST_USER_ID, Either.Left(StorageFailure.DataNotFound))
            .arrange()

        assertFalse(useCase())
    }

    private class Arrangement {

        private val sessionRepository = mock(SessionRepository::class)
        private val useCase by lazy { IsCurrentSessionNomadAccountUseCase(sessionRepository) }

        suspend fun withCurrentSession(result: Either<StorageFailure, AccountInfo>) = apply {
            coEvery { sessionRepository.currentSession() }.returns(result)
        }

        suspend fun withFullAccountInfo(userId: UserId, result: Either<StorageFailure, Account>) = apply {
            every { sessionRepository.fullAccountInfo(eq(userId)) }.returns(result)
        }

        fun arrange() = this to useCase
    }

    companion object {
        private val TEST_USER_ID = UserId("test", "domain")

        private val TEST_SERVER_CONFIG = ServerConfig(
            id = "config-id",
            links = ServerConfig.STAGING,
            metaData = ServerConfig.MetaData(
                federation = false,
                commonApiVersion = CommonApiVersionType.Valid(version = 1),
                domain = "domain"
            )
        )

        private fun testAccount(nomadServiceUrl: String?) = Account(
            info = AccountInfo.Valid(TEST_USER_ID),
            serverConfig = TEST_SERVER_CONFIG,
            ssoId = null,
            nomadServiceUrl = nomadServiceUrl
        )
    }
}
