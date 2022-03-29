package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class UpdateCurrentSessionUseCaseTest {

    @Mock
    val sessionRepository: SessionRepository = configure(mock(classOf<SessionRepository>())) { stubsUnitByDefault = true }

    lateinit var updateCurrentSessionUseCase: UpdateCurrentSessionUseCase

    @BeforeTest
    fun setup() {
        updateCurrentSessionUseCase = UpdateCurrentSessionUseCase(sessionRepository)
    }

    @Test
    fun givenAUserId_whenUpdateCurrentSessionUseCaseIsInvoked_thenUpdateCurrentSessionIsCalled() = runTest {
        val userId = "user_id"
        given(sessionRepository).invocation { updateCurrentSession(userId) }.then { Either.Right(Unit) }

        updateCurrentSessionUseCase("user_id")

        verify(sessionRepository).invocation { updateCurrentSession(userId) }.wasInvoked(exactly = once)
    }
}
