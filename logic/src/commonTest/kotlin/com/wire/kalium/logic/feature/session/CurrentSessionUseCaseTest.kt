package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentSessionUseCaseTest {
    @Mock
    val sessionRepository = mock(classOf<SessionRepository>())

    lateinit var currentSessionUseCase: CurrentSessionUseCase

    @BeforeTest
    fun setup() {
        currentSessionUseCase = CurrentSessionUseCase(sessionRepository)
    }

    @Test
    fun givenAUserID_whenCurrentSessionSuccess_thenTheSuccessIsPropagated() = runTest {
        val expected: AccountInfo = TEST_Account_INFO

        given(sessionRepository).coroutine { currentSession() }.then { Either.Right(expected) }

        val actual = currentSessionUseCase()

        assertIs<CurrentSessionResult.Success>(actual)
        assertEquals(expected, actual.accountInfo)

        verify(sessionRepository).coroutine { currentSession() }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserID_whenCurrentSessionFailWithNoSessionFound_thenTheErrorIsPropagated() = runTest {
        val expected: StorageFailure = StorageFailure.DataNotFound

        given(sessionRepository).coroutine { currentSession() }.then { Either.Left(expected) }

        val actual = currentSessionUseCase()

        assertIs<CurrentSessionResult.Failure.SessionNotFound>(actual)

        verify(sessionRepository).coroutine { currentSession() }.wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_Account_INFO = AccountInfo.Valid(userId = UserId("test", "domain"))
    }
}
