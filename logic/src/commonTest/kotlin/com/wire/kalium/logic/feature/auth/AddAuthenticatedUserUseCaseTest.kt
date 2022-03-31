package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
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
    private lateinit var addAuthenticatedUserUseCase: AddAuthenticatedUserUseCase

    @BeforeTest
    fun setup() {
        addAuthenticatedUserUseCase = AddAuthenticatedUserUseCase(sessionRepository)
    }

    @Test
    fun givenUserWithNoAlreadyStoredSession_whenInvoked_thenSuccessIsReturned() = runTest {
        val session = TEST_SESSION
        given(sessionRepository).invocation { doesSessionExist(session.userId) }.then { Either.Right(false) }

        given(sessionRepository).invocation { storeSession(session) }.then { Either.Right(Unit) }
        given(sessionRepository).invocation { updateCurrentSession(session.userId) }.then { Either.Right(Unit) }

        val actual = addAuthenticatedUserUseCase(session, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(sessionRepository).invocation { storeSession(session) }.wasInvoked(exactly = once)
        verify(sessionRepository).invocation { updateCurrentSession(session.userId) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvoked_thenUserAlreadyExistsIsReturned() = runTest {
        val session = TEST_SESSION
        given(sessionRepository).invocation { doesSessionExist(session.userId) }.then { Either.Right(true) }

        val actual = addAuthenticatedUserUseCase(session, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).function(sessionRepository::storeSession).with(any()).wasNotInvoked()
        verify(sessionRepository).function(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvokedWithReplace_thenSuccessReturned() = runTest {
        val oldSession = AuthSession(TEST_USERID, "access-token", "refresh-token", "type", TEST_SERVER_CONFIG)
        val newSession = TEST_SESSION
        given(sessionRepository).invocation { doesSessionExist(newSession.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { userSession(newSession.userId) }.then { Either.Right(oldSession) }
        given(sessionRepository).invocation { storeSession(newSession) }.then { Either.Right(Unit) }
        given(sessionRepository).invocation { updateCurrentSession(newSession.userId) }.then { Either.Right(Unit) }

        val actual = addAuthenticatedUserUseCase(newSession, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(sessionRepository).invocation { storeSession(newSession) }.wasInvoked(exactly = once)
        verify(sessionRepository).invocation { updateCurrentSession(newSession.userId) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSessionWithDifferentServerConfig_whenInvokedWithReplace_thenUserAlreadyExistsReturned() = runTest {
        val oldSession = AuthSession(
            TEST_USERID, "access-token", "refresh-token", "type",
            ServerConfig("apiBaseUrl", "accountsBaseUrl", "webSocketBaseUrl", "blackListUrl", "teamsUrl", "websiteUrl", "title")
        )
        val newSession = TEST_SESSION
        given(sessionRepository).invocation { doesSessionExist(newSession.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { userSession(newSession.userId) }.then { Either.Right(oldSession) }

        val actual = addAuthenticatedUserUseCase(newSession, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).function(sessionRepository::storeSession).with(any()).wasNotInvoked()
        verify(sessionRepository).function(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
    }


    private companion object {
        val TEST_USERID = UserId("user_id", "domain.de")
        val TEST_SERVER_CONFIG: ServerConfig = ServerConfig(
            apiBaseUrl = "apiBaseUrl.com",
            accountsBaseUrl = "accountsUrl.com",
            webSocketBaseUrl = "webSocketUrl.com",
            blackListUrl = "blackListUrl.com",
            teamsUrl = "teamsUrl.com",
            websiteUrl = "websiteUrl.com",
            title = "Test Title"
        )
        val TEST_SESSION =
            AuthSession(
                userId = TEST_USERID,
                accessToken = "access_token",
                refreshToken = "refresh_token",
                tokenType = "token_type",
                TEST_SERVER_CONFIG
            )
    }

}
