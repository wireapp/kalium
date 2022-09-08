package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SelfServerConfigUseCaseTest {

    @Test
    fun givenUserSession_whenGetSelfServerConfig_thenReturnSelfServerConfig() = runTest {
        val expected = validAuthSessionWith(selfUserId)
        val (arrangement, selfServerConfigUseCase) = Arrangement()
            .withUserSessionReturnSuccess(expected)
            .arrange()

        selfServerConfigUseCase().also { result ->
            assertIs<SelfServerConfigUseCase.Result.Success>(result)
            assertEquals(expected.serverLinks, result.serverLinks)
        }

        verify(arrangement.sessionRepository)
            .function(arrangement.sessionRepository::userSession)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenGetSelfServerConfig_thenReturnError() = runTest {
        val (arrangement, selfServerConfigUseCase) = Arrangement()
            .withUserSessionReturnError(StorageFailure.DataNotFound)
            .arrange()

        selfServerConfigUseCase().also { result ->
            assertIs<SelfServerConfigUseCase.Result.Failure>(result)
            assertEquals(StorageFailure.DataNotFound, result.cause)
        }

        verify(arrangement.sessionRepository)
            .function(arrangement.sessionRepository::userSession)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private companion object {
        private val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)

        fun validAuthSessionWith(userId: UserId): AuthSession =
            AuthSession(
                AuthSession.Token.Valid(
                    userId,
                    "accessToken",
                    "refreshToken",
                    "token_type",
                ),
                TEST_SERVER_CONFIG.links
            )

        val selfUserId = UserId("self_id", "self_domain")
    }

    private class Arrangement {

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        val selfServerConfigUseCase = SelfServerConfigUseCase(sessionRepository, selfUserId)
        fun withUserSessionReturnSuccess(session: AuthSession): Arrangement = apply {
            given(sessionRepository)
                .invocation { sessionRepository.userSession(selfUserId) }
                .then { Either.Right(session) }
        }
        fun withUserSessionReturnError(storageFailure: StorageFailure): Arrangement = apply {
            given(sessionRepository)
                .invocation { sessionRepository.userSession(selfUserId) }
                .then { Either.Left(storageFailure) }
        }
        fun arrange() = this to selfServerConfigUseCase
    }
}
