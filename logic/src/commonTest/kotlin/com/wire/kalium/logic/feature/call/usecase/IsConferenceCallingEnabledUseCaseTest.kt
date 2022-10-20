package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsConferenceCallingEnabledUseCaseTest {

    @Mock
    val userConfigRepository = mock(classOf<UserConfigRepository>())

    private lateinit var observeIsConferenceCallingEnabled: IsConferenceCallingEnabledUseCase

    @BeforeTest
    fun setUp() {
        observeIsConferenceCallingEnabled = IsConferenceCallingEnabledUseCaseImpl(
            userConfigRepository = userConfigRepository
        )
    }

    @Test
    fun givenAnStorageErrorOccurred_whenInvokingObserveIsConferenceCallingEnabled_thenReturnFalse() = runTest {
        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabled)
            .whenInvoked()
            .thenReturn(Either.Left(StorageFailure.Generic(Throwable("error"))))

        val result = observeIsConferenceCallingEnabled()

        assertEquals(false, result)
    }

    @Test
    fun givenUserTeamDoesntHaveConferenceCallingEnabled_whenInvokingObserveIsConferenceCallingEnabled_thenReturnFalse() = runTest {
        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabled)
            .whenInvoked()
            .thenReturn(Either.Right(false))

        val result = observeIsConferenceCallingEnabled()

        assertEquals(false, result)
    }

    @Test
    fun givenUserTeamHaveConferenceCallingEnabled_whenInvokingObserveIsConferenceCallingEnabled_thenReturnTrue() = runTest {
        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabled)
            .whenInvoked()
            .thenReturn(Either.Right(true))

        val result = observeIsConferenceCallingEnabled()

        assertEquals(true, result)
    }
}
