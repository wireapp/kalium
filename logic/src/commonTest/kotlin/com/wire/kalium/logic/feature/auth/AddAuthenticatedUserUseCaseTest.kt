package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
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
    private lateinit var addAuthenticatedUserUseCase: AddAuthenticatedUserUseCase

    @BeforeTest
    fun setup() {
        addAuthenticatedUserUseCase = AddAuthenticatedUserUseCase(sessionRepository)
    }

    @Test
    fun givenUserWithNoAlreadyStoredSession_whenInvoked_thenSuccessIsReturned() = runTest {
        val session = TEST_Token
        given(sessionRepository).invocation { doesSessionExist(session.token.userId) }.then { Either.Right(false) }

        given(sessionRepository).invocation { storeSession(session, TEST_SSO_ID) }.then { Either.Right(Unit) }
        given(sessionRepository).invocation { updateCurrentSession(session.token.userId) }.then { Either.Right(Unit) }

        val actual = addAuthenticatedUserUseCase(session, TEST_SSO_ID, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(sessionRepository).invocation { storeSession(session, TEST_SSO_ID) }.wasInvoked(exactly = once)
        verify(sessionRepository).invocation { updateCurrentSession(session.token.userId) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvoked_thenUserAlreadyExistsIsReturned() = runTest {
        val session = TEST_Token
        given(sessionRepository).invocation { doesSessionExist(session.token.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { userSession(session.token.userId) }.then { Either.Left(StorageFailure.DataNotFound) }

        val actual = addAuthenticatedUserUseCase(session, TEST_SSO_ID, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).function(sessionRepository::storeSession).with(any(), any()).wasNotInvoked()
        verify(sessionRepository).function(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvokedWithReplace_thenSuccessReturned() = runTest {
        val oldToken =
            AuthSession(AuthSession.Token.Valid(TEST_USERID, "access-token", "refresh-token", "type"), TEST_SERVER_CONFIG.links)
        val newSession = TEST_Token
        given(sessionRepository).invocation { doesSessionExist(newSession.token.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { userSession(newSession.token.userId) }.then { Either.Right(oldToken) }
        given(sessionRepository).invocation { storeSession(newSession, TEST_SSO_ID) }.then { Either.Right(Unit) }
        given(sessionRepository).invocation { updateCurrentSession(newSession.token.userId) }.then { Either.Right(Unit) }

        val actual = addAuthenticatedUserUseCase(newSession, TEST_SSO_ID, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(sessionRepository).invocation { storeSession(newSession, TEST_SSO_ID) }.wasInvoked(exactly = once)
        verify(sessionRepository).invocation { updateCurrentSession(newSession.token.userId) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSessionWithDifferentServerConfig_whenInvokedWithReplace_thenUserAlreadyExistsReturned() = runTest {
        val oldToken = AuthSession(
            AuthSession.Token.Valid(TEST_USERID, "access-token", "refresh-token", "type"),
            newServerConfig(999).links
        )
        val newSession = TEST_Token
        given(sessionRepository).invocation { doesSessionExist(newSession.token.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { userSession(newSession.token.userId) }.then { Either.Right(oldToken) }

        val actual = addAuthenticatedUserUseCase(newSession, TEST_SSO_ID, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).function(sessionRepository::storeSession).with(any(), any()).wasNotInvoked()
        verify(sessionRepository).function(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
    }

    private companion object {
        val TEST_USERID = UserId("user_id", "domain.de")
        val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)
        val TEST_Token =
            AuthSession(
                AuthSession.Token.Valid(
                    userId = TEST_USERID,
                    accessToken = "access_token",
                    refreshToken = "refresh_token",
                    tokenType = "token_type",
                ),
                TEST_SERVER_CONFIG.links
            )
        val TEST_SSO_ID = SsoId(
            "scim",
            null,
            null
        )
    }
}
