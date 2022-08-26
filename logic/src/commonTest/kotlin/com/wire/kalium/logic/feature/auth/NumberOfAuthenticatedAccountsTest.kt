package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NumberOfAuthenticatedAccountsTest {

    @Test
    fun givenNoAuthenticatedAccounts_whenCheckingNumberOfAuthenticatedAccounts_thenReturnZero() = runTest {
        val (arrangement, numberOfAuthenticatedAccounts) = Arrangement().withAllSessionSuccess(emptyList()).arrange()

        numberOfAuthenticatedAccounts().also {
            assertIs<NumberOfAuthenticatedAccountsUseCase.Result.Success>(it)
            assertEquals(0, it.count)
        }

        verify(arrangement.sessionRepository).invocation { arrangement.sessionRepository.allSessions() }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidAndInvalidSessions_whenCheckingNumberOfAuthenticatedAccounts_thenReturnTheCountOfValidSessionsOnly() = runTest {

        val (validSessionList, validCount) = listOf<AuthSession>(
            validAuthSession,
            validAuthSession,
            validAuthSession,
            validAuthSession,
            validAuthSession
        ).let {
            it to it.count()
        }

        val invalidSessionList = listOf<AuthSession>(
            invalidAuthSession,
            invalidAuthSession,
            invalidAuthSession,
        )
        val (arrangement, numberOfAuthenticatedAccounts) = Arrangement()
            .withAllSessionSuccess(validSessionList + invalidSessionList)
            .arrange()

        numberOfAuthenticatedAccounts().also {
            assertIs<NumberOfAuthenticatedAccountsUseCase.Result.Success>(it)
            assertEquals(validCount, it.count)
        }

        verify(arrangement.sessionRepository).invocation { arrangement.sessionRepository.allSessions() }.wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)

        val validAuthSession: AuthSession =
            AuthSession(
                AuthSession.Session.Valid(
                    UserId("user_id", "domain.de"),
                    "access_token",
                    "refresh_token",
                    "token_type",
                ),
                TEST_SERVER_CONFIG.links
            )

        val invalidAuthSession: AuthSession = AuthSession(
            session = AuthSession.Session.Invalid(
                UserId("user_id", "domain.de"),
                reason = LogoutReason.DELETED_ACCOUNT,
                false
            ),
            serverLinks = TEST_SERVER_CONFIG.links
        )
    }

    private class Arrangement {
        @Mock
        val sessionRepository: SessionRepository = mock(SessionRepository::class)

        val numberOfAuthenticatedAccountsUseCase = NumberOfAuthenticatedAccountsUseCase(sessionRepository)

        fun withAllSessionSuccess(authSessionList: List<AuthSession>): Arrangement = apply {
            given(sessionRepository).invocation { sessionRepository.allSessions() }.then { Either.Right(authSessionList) }
        }

        fun arrange() = this to numberOfAuthenticatedAccountsUseCase
    }

}
