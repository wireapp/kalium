package com.wire.kalium.logic.feature.session

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentSessionFlowUseCaseTest {
    @Mock
    val sessionRepository: SessionRepository = mock(classOf<SessionRepository>())

    lateinit var currentSessionFlowUseCase: CurrentSessionFlowUseCaseImpl

    @BeforeTest
    fun setup() {
        currentSessionFlowUseCase = CurrentSessionFlowUseCaseImpl(sessionRepository)
    }

    @Test
    fun givenAUserID_whenCurrentSessionSuccess_thenTheSuccessIsPropagated() = runTest {
        val expected: AuthSession = randomAuthSession()

        given(sessionRepository).invocation { currentSessionFlow() }.then { flowOf(Either.Right(expected)) }

        currentSessionFlowUseCase()
            .test {
                val result = awaitItem()
                assertIs<CurrentSessionResult.Success>(result)
                assertEquals(expected, result.authSession)

                verify(sessionRepository).invocation { currentSessionFlow() }.wasInvoked(exactly = once)
                awaitComplete()
            }
    }

    @Test
    fun givenAUserID_whenCurrentSessionFailWithNoSessionFound_thenTheErrorIsPropagated() = runTest {
        val expected: StorageFailure = StorageFailure.DataNotFound

        given(sessionRepository).invocation { currentSessionFlow() }.then { flowOf(Either.Left(expected)) }

        currentSessionFlowUseCase().test {
            assertIs<CurrentSessionResult.Failure.SessionNotFound>(awaitItem())

            verify(sessionRepository).invocation { currentSessionFlow() }.wasInvoked(exactly = once)
            awaitComplete()
        }
    }


    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)

        fun randomAuthSession(): AuthSession =
            AuthSession(UserId("user_id", "domain.de"), randomString, randomString, randomString, TEST_SERVER_CONFIG)
    }
}
