package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.StorageFailure
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

        val actual = addAuthenticatedUserUseCase(TEST_SERVER_CONFIG.id, TEST_SSO_ID, tokens,false)

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
        given(sessionRepository).coroutine { doesSessionExist(session.session.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { (session.session.userId) }.then { Either.Left(StorageFailure.DataNotFound) }

        val actual = addAuthenticatedUserUseCase(session, TEST_SSO_ID, false)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).function(sessionRepository::storeSession).with(any(), any()).wasNotInvoked()
        verify(sessionRepository).function(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
    }

    @Test
    fun givenUserWithAlreadyStoredSession_whenInvokedWithReplace_thenSuccessReturned() = runTest {
        val oldSession =
            AuthSession(AuthSession.Session.Valid(TEST_USERID, "access-token", "refresh-token", "type"), TEST_SERVER_CONFIG.links)
        val newSession = TEST_SESSION
        given(sessionRepository).coroutine { doesSessionExist(newSession.session.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { userSession(newSession.session.userId) }.then { Either.Right(oldSession) }
        given(sessionRepository).coroutine { storeSession(newSession, TEST_SSO_ID) }.then { Either.Right(Unit) }
        given(sessionRepository).coroutine { updateCurrentSession(newSession.session.userId) }.then { Either.Right(Unit) }

        val actual = addAuthenticatedUserUseCase(newSession, TEST_SSO_ID, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Success>(actual)

        verify(sessionRepository).coroutine { storeSession(newSession, TEST_SSO_ID) }.wasInvoked(exactly = once)
        verify(sessionRepository).coroutine { updateCurrentSession(newSession.session.userId) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithAlreadyStoredSessionWithDifferentServerConfig_whenInvokedWithReplace_thenUserAlreadyExistsReturned() = runTest {
        val oldSession = AuthSession(
            AuthSession.Session.Valid(TEST_USERID, "access-token", "refresh-token", "type"),
            newServerConfig(999).links
        )
        val newSession = TEST_SESSION
        given(sessionRepository).coroutine { doesSessionExist(newSession.session.userId) }.then { Either.Right(true) }
        given(sessionRepository).invocation { userSession(newSession.session.userId) }.then { Either.Right(oldSession) }

        val actual = addAuthenticatedUserUseCase(newSession, TEST_SSO_ID, true)

        assertIs<AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists>(actual)

        verify(sessionRepository).suspendFunction(sessionRepository::storeSession).with(any(), any(), any()).wasNotInvoked()
        verify(sessionRepository).suspendFunction(sessionRepository::updateCurrentSession).with(any()).wasNotInvoked()
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
