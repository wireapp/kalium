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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.token.AccessToken
import com.wire.kalium.logic.data.session.token.RefreshToken
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class AddAuthenticatedUserUseCaseTest {

    @Test
    fun givenUserWithNoAlreadyStoredSession_whenInvoked_thenSuccessIsReturned() = runTest {
        val tokens = TEST_AUTH_TOKENS
        val proxyCredentials = PROXY_CREDENTIALS
        val (arrangement, addAuthenticatedUserUseCase) = Arrangement()
            .withDoesValidSessionExistResult(tokens.userId, Either.Right(false))
            .withStoreSessionResult(TEST_SERVER_CONFIG.id, TEST_SSO_ID, tokens, proxyCredentials, Either.Right(Unit))
            .withUpdateCurrentSessionResult(tokens.userId, Either.Right(Unit))
            .arrange()

        val actual = addAuthenticatedUserUseCase(TEST_SERVER_CONFIG.id, TEST_SSO_ID, tokens, proxyCredentials, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::storeSession)
            .with(any(), any(), any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::updateCurrentSession)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::doesValidSessionExist)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvoked_thenUserAlreadyExistsIsReturned() = runTest {
        val tokens = TEST_AUTH_TOKENS
        val proxyCredentials = PROXY_CREDENTIALS

        val (arrangement, addAuthenticatedUserUseCase) = Arrangement()
            .withDoesValidSessionExistResult(tokens.userId, Either.Right(true))
            .arrange()

        val actual = addAuthenticatedUserUseCase(TEST_SERVER_CONFIG.id, TEST_SSO_ID, tokens, proxyCredentials, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::storeSession)
            .with(any(), any(), any(), any())
            .wasNotInvoked()

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::updateCurrentSession)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::doesValidSessionExist)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvokedWithReplaceAndServerConfigAreTheSame_thenSuccessReturned() = runTest {
        val oldSession = TEST_AUTH_TOKENS.copy(
            accessToken = AccessToken("oldAccessToken", TEST_AUTH_TOKENS.tokenType),
            refreshToken = RefreshToken("oldRefreshToken")
        )
        val oldSessionFullInfo = Account(AccountInfo.Valid(oldSession.userId), TEST_SERVER_CONFIG, TEST_SSO_ID)

        val newSession = TEST_AUTH_TOKENS.copy(
            accessToken = AccessToken("newAccessToken", TEST_AUTH_TOKENS.tokenType),
            refreshToken = RefreshToken("newRefreshToken")
        )

        val proxyCredentials = PROXY_CREDENTIALS


        val (arrangement, addAuthenticatedUserUseCase) = Arrangement()
            .withDoesValidSessionExistResult(newSession.userId, Either.Right(true))
            .withStoreSessionResult(TEST_SERVER_CONFIG.id, TEST_SSO_ID, newSession, proxyCredentials, Either.Right(Unit))
            .withUpdateCurrentSessionResult(newSession.userId, Either.Right(Unit))
            .withFullAccountInfoResult(newSession.userId, Either.Right(oldSessionFullInfo))
            .withConfigByIdSuccess(TEST_SERVER_CONFIG.id, TEST_SERVER_CONFIG_ENTITY)
            .arrange()

        val actual = addAuthenticatedUserUseCase(TEST_SERVER_CONFIG.id, TEST_SSO_ID, newSession, proxyCredentials, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::storeSession)
            .with(any(), any(), any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::updateCurrentSession)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.serverConfigurationDAO)
            .function(arrangement.serverConfigurationDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.sessionRepository)
            .function(arrangement.sessionRepository::fullAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::doesValidSessionExist)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSessionWithDifferentServerConfig_whenInvokedWithReplace_thenUserAlreadyExistsReturned() = runTest {
        val serverConfigMapper = MapperProvider.serverConfigMapper()

        val oldSession = TEST_AUTH_TOKENS.copy(
            accessToken = AccessToken("oldAccessToken", TEST_AUTH_TOKENS.tokenType),
            refreshToken = RefreshToken("oldRefreshToken")
        )
        val oldSessionServer = newServerConfig(id = 11)
        val oldSessionServerEntity = oldSessionServer.let {
            serverConfigMapper.toEntity(it)
        }

        val newSession = TEST_AUTH_TOKENS.copy(
            accessToken = AccessToken("newAccessToken", TEST_AUTH_TOKENS.tokenType),
            refreshToken = RefreshToken("newRefreshToken")
        )
        val newSessionServer = newServerConfig(id = 22)
        val newSessionServerEntity = newSessionServer.let {
            serverConfigMapper.toEntity(it)
        }

        val proxyCredentials = PROXY_CREDENTIALS

        val (arrangement, addAuthenticatedUserUseCase) = Arrangement()
            .withDoesValidSessionExistResult(newSession.userId, Either.Right(true))
            .withConfigForUserIdSuccess(oldSession.userId.toDao(), oldSessionServerEntity)
            .withConfigByIdSuccess(newSessionServer.id, newSessionServerEntity)
            .withFullAccountInfoResult(
                oldSession.userId,
                Either.Right(Account(AccountInfo.Valid(oldSession.userId), oldSessionServer, TEST_SSO_ID))
            )
            .arrange()

        val actual = addAuthenticatedUserUseCase(newSessionServer.id, TEST_SSO_ID, newSession, proxyCredentials, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::doesValidSessionExist)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::storeSession)
            .with(any(), any(), any(), any())
            .wasNotInvoked()
        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::updateCurrentSession)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.sessionRepository)
            .function(arrangement.sessionRepository::fullAccountInfo).with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigurationDAO)
            .function(arrangement.serverConfigurationDAO::configById).with(any())
            .wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_USERID = UserId("user_id", "domain.de")
        val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)
        val TEST_SERVER_CONFIG_ENTITY = TEST_SERVER_CONFIG.let {
            MapperProvider.serverConfigMapper().toEntity(it)
        }
        val TEST_AUTH_TOKENS = AccountTokens(
            TEST_USERID,
            "access-token",
            "refresh-token",
            "type",
            "cookie-label"
        )
        val PROXY_CREDENTIALS = ProxyCredentials("user_name", "password")
        val TEST_SSO_ID = SsoId(
            "scim",
            null,
            null
        )
    }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val serverConfigurationDAO = mock(ServerConfigurationDAO::class)

        private val addAuthenticatedUserUseCase = AddAuthenticatedUserUseCase(sessionRepository, serverConfigurationDAO)

        suspend fun withDoesValidSessionExistResult(
            userId: UserId,
            result: Either<StorageFailure, Boolean>
        ) = apply {
            given(sessionRepository).coroutine { doesValidSessionExist(userId) }.then { result }
        }

        suspend fun withConfigForUserIdSuccess(
            userId: UserIDEntity,
            result: ServerConfigEntity
        ) = apply {
            given(serverConfigurationDAO).coroutine { configForUser(userId) }.then { result }
        }

        fun withFullAccountInfoResult(
            userId: UserId,
            result: Either<StorageFailure, Account>
        ) = apply {
            given(sessionRepository).invocation { fullAccountInfo(userId) }.then { result }
        }

        fun withConfigByIdSuccess(
            serverConfigId: String,
            result: ServerConfigEntity
        ) = apply {
            given(serverConfigurationDAO).invocation { configById(serverConfigId) }.then { result }
        }

        suspend fun withStoreSessionResult(
            serverConfigId: String,
            ssoId: SsoId?,
            accountTokens: AccountTokens,
            proxyCredentials: ProxyCredentials?,
            result: Either<StorageFailure, Unit>
        ) = apply {
            given(sessionRepository).coroutine { storeSession(serverConfigId, ssoId, accountTokens, proxyCredentials) }.then { result }
        }

        suspend fun withUpdateCurrentSessionResult(
            userId: UserId,
            result: Either<StorageFailure, Unit>
        ) = apply {
            given(sessionRepository).coroutine { updateCurrentSession(userId) }.then { result }
        }

        fun arrange() = this to addAuthenticatedUserUseCase
    }
}
