package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AddAuthenticatedUserUseCaseTest {

    @Mock
    private val sessionRepository = mock(classOf<SessionRepository>())

    @Mock
    private val serverConfigRepository = mock(ServerConfigRepository::class)
    private lateinit var addAuthenticatedUserUseCase: AddAuthenticatedUserUseCase

    @BeforeTest
    fun setup() {
        addAuthenticatedUserUseCase = AddAuthenticatedUserUseCase(sessionRepository, serverConfigRepository)
    }

    @Test
    fun givenUserWithNoAlreadyStoredSession_whenInvoked_thenSuccessIsReturned() = runTest {
        val tokens = TEST_AUTH_TOKENS
        given(sessionRepository).coroutine { doesSessionExist(tokens.userId) }.then { Either.Right(false) }

        given(sessionRepository).coroutine { storeSession(TEST_SERVER_CONFIG.id, TEST_SSO_ID, tokens) }.then { Either.Right(Unit) }
        given(sessionRepository).coroutine { updateCurrentSession(tokens.userId) }.then { Either.Right(Unit) }

        val actual = addAuthenticatedUserUseCase(TEST_SERVER_CONFIG.id, TEST_SSO_ID, tokens, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(sessionRepository)
            .suspendFunction(sessionRepository::storeSession)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)

        verify(sessionRepository)
            .suspendFunction(sessionRepository::updateCurrentSession)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvoked_thenUserAlreadyExistsIsReturned() = runTest {
        val tokens = TEST_AUTH_TOKENS
        given(sessionRepository).coroutine { doesSessionExist(tokens.userId) }.then { Either.Right(true) }

        val actual = addAuthenticatedUserUseCase(TEST_SERVER_CONFIG.id, TEST_SSO_ID, tokens, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).suspendFunction(sessionRepository::storeSession).with(any(), any(), any()).wasNotInvoked()
        verify(sessionRepository).suspendFunction(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvokedWithReplaceAndServerConfigAreTheSame_thenSuccessReturned() = runTest {
        val oldSession = TEST_AUTH_TOKENS.copy(accessToken = "oldAccessToken", refreshToken = "oldRefreshToken")
        val oldSessionFullInfo = Account(AccountInfo.Valid(oldSession.userId), TEST_SERVER_CONFIG, TEST_SSO_ID)

        val newSession = TEST_AUTH_TOKENS.copy(accessToken = "newAccessToken", refreshToken = "newRefreshToken")

        given(sessionRepository).coroutine { doesSessionExist(newSession.userId) }.then { Either.Right(true) }
        given(sessionRepository).coroutine { storeSession(TEST_SERVER_CONFIG.id, TEST_SSO_ID, newSession) }.then { Either.Right(Unit) }
        given(sessionRepository).coroutine { updateCurrentSession(newSession.userId) }.then { Either.Right(Unit) }
        given(sessionRepository).invocation { fullAccountInfo(oldSession.userId) }.then { Either.Right(oldSessionFullInfo) }
        given(serverConfigRepository).invocation { configById(TEST_SERVER_CONFIG.id) }.then { Either.Right(TEST_SERVER_CONFIG) }

        val actual = addAuthenticatedUserUseCase(TEST_SERVER_CONFIG.id, TEST_SSO_ID, newSession, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(sessionRepository)
            .suspendFunction(sessionRepository::storeSession)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)

        verify(sessionRepository)
            .suspendFunction(sessionRepository::updateCurrentSession)
            .with(any())
            .wasInvoked(exactly = once)

        verify(serverConfigRepository)
            .function(serverConfigRepository::configById)
            .with(any())
            .wasInvoked(exactly = once)

        verify(sessionRepository)
            .function(sessionRepository::fullAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSessionWithDifferentServerConfig_whenInvokedWithReplace_thenUserAlreadyExistsReturned() = runTest {
        val oldSession = TEST_AUTH_TOKENS.copy(accessToken = "oldAccessToken", refreshToken = "oldRefreshToken")
        val oldSessionServer = newServerConfig(id = 11)

        val newSession = TEST_AUTH_TOKENS.copy(accessToken = "newAccessToken", refreshToken = "newRefreshToken")
        val newSessionServer = newServerConfig(id = 22)

        given(sessionRepository).coroutine { doesSessionExist(newSession.userId) }.then { Either.Right(true) }
        given(serverConfigRepository).coroutine { configForUser(oldSession.userId) }.then { Either.Right(oldSessionServer) }
        given(sessionRepository).invocation { fullAccountInfo(oldSession.userId) }.then { Either.Right(Account(AccountInfo.Valid(oldSession.userId), oldSessionServer, TEST_SSO_ID)) }
        given(serverConfigRepository).invocation { configById(newSessionServer.id) }.then { Either.Right(newSessionServer) }

        val actual = addAuthenticatedUserUseCase(newSessionServer.id, TEST_SSO_ID, newSession, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).suspendFunction(sessionRepository::storeSession).with(any(), any(), any()).wasNotInvoked()
        verify(sessionRepository).suspendFunction(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
        verify(sessionRepository).function(sessionRepository::fullAccountInfo).with(any()).wasInvoked(exactly = once)
        verify(serverConfigRepository).function(serverConfigRepository::configById).with(any()).wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_USERID = UserId("user_id", "domain.de")
        val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)
        val TEST_AUTH_TOKENS = AuthTokens(TEST_USERID, "access-token", "refresh-token", "type")
        val TEST_SSO_ID = SsoId(
            "scim",
            null,
            null
        )
    }
}
