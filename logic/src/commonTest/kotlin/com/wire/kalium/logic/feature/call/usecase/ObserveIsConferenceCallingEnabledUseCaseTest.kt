package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveIsConferenceCallingEnabledUseCaseTest {

    @Mock
    val userConfigRepository = mock(classOf<UserConfigRepository>())

    private lateinit var observeIsConferenceCallingEnabled: ObserveIsConferenceCallingEnabledUseCase

    @BeforeTest
    fun setUp() {
        observeIsConferenceCallingEnabled = ObserveIsConferenceCallingEnabledUseCaseImpl(
            userConfigRepository = userConfigRepository
        )
    }

    @Test
    fun givenAnStorageErrorOccurred_whenInvokingObserveIsConferenceCallingEnabled_thenReturnFalse() = runTest {
        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabledFlow)
            .whenInvoked()
            .thenReturn(flowOf(Either.Left(StorageFailure.Generic(Throwable("error")))))

        val result = observeIsConferenceCallingEnabled()

        result.test {
            assertEquals(false, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenUserTeamDoesntHaveConferenceCallingEnabled_whenInvokingObserveIsConferenceCallingEnabled_thenReturnFalse() = runTest {
        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabledFlow)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(false)))

        val result = observeIsConferenceCallingEnabled()

        result.test {
            assertEquals(false, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenUserTeamHaveConferenceCallingEnabled_whenInvokingObserveIsConferenceCallingEnabled_thenReturnTrue() = runTest {
        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabledFlow)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(true)))

        val result = observeIsConferenceCallingEnabled()

        result.test {
            assertEquals(true, awaitItem())
            awaitComplete()
        }
    }
}
