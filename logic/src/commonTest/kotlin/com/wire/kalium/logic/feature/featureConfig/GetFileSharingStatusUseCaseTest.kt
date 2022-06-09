package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FileSharingModel
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class GetFileSharingStatusUseCaseTest {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withSuccessfulResponse()
            .arrange()

        // When
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertTrue(actual is GetFileSharingStatusResult.Success)
        assertEquals(arrangement.fileSharingModel, actual.fileSharingModel)

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithInvalidCredentials_thenOperationDeniedIsReturned() = runTest {
        // Given
        val operationDeniedException = TestNetworkException.operationDenied
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withGetFileSharingStatusErrorResponse(operationDeniedException)
            .arrange()

        // When
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertTrue(actual is GetFileSharingStatusResult.Failure.OperationDenied)
        assertEquals(arrangement.operationDeniedFailure, actual)

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithUserThatNotInTheTeam_thenNoTeamIsReturned() = runTest {
        // Given
        val noTeamException = TestNetworkException.noTeam
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withGetFileSharingStatusErrorResponse(noTeamException)
            .arrange()

        // When
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertTrue(actual is GetFileSharingStatusResult.Failure.NoTeam)
        assertEquals(arrangement.noTeamFailure, actual)

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }


    private class Arrangement {
        val fileSharingModel = FileSharingModel("locked", "enabled")
        val operationDeniedFailure = GetFileSharingStatusResult.Failure.OperationDenied
        val noTeamFailure = GetFileSharingStatusResult.Failure.NoTeam

        @Mock
        val featureConfigRepository = mock(classOf<FeatureConfigRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())


        val getFileSharingStatusUseCase =
            GetFileSharingStatusUseCaseImpl(userConfigRepository, featureConfigRepository)

        fun withSuccessfulResponse(): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::setFileSharingStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))

            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFileSharingFeatureConfig).whenInvoked()
                .thenReturn(Either.Right(fileSharingModel))
            return this
        }

        fun withGetFileSharingStatusErrorResponse(exception: KaliumException): Arrangement {
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFileSharingFeatureConfig)
                .whenInvoked()
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to getFileSharingStatusUseCase
    }
}
