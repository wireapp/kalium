package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FileSharingModel
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageDownloadStatusUseCaseTest
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        assertIs<GetFileSharingStatusResult.Success>(actual)
        assertEquals(arrangement.fileSharingModel, actual.fileSharingModel)

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithInvalidCredentials_thenOperationDeniedIsReturned() = runTest {
        // Given
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withOperationDeniedErrorResponse()
            .arrange()

        // When
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertIs<GetFileSharingStatusResult.Failure.OperationDenied>(actual)
        assertEquals(arrangement.operationDeniedFailure, actual)

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithUserThatNotInTheTeam_thenNoTeamIsReturned() = runTest {
        // Given
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withNoTeamErrorResponse()
            .arrange()

        // When
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertIs<GetFileSharingStatusResult.Failure.NoTeam>(actual)
        assertEquals(arrangement.noTeamFailure, actual)

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }


    private class Arrangement {

        val fileSharingModel = FileSharingModel("locked", "enabled")
        val operationDeniedFailure = GetFileSharingStatusResult.Failure.OperationDenied
        val noTeamFailure = GetFileSharingStatusResult.Failure.NoTeam

        private val operationDeniedErrorResponse = KaliumException.InvalidRequestError(
            ErrorResponse(
                403, "Insufficient permissions", "operation-denied"
            )
        )
        private val noTeamErrorResponse = KaliumException.InvalidRequestError(
            ErrorResponse(
                404, "Team not found", "no-team"
            )
        )

        @Mock
        val featureConfigRepository = mock(classOf<FeatureConfigRepository>())

        val getFileSharingStatusUseCase =
            GetFileSharingStatusUseCaseImpl(featureConfigRepository)


        fun withSuccessfulResponse(): Arrangement {
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFileSharingFeatureConfig).whenInvoked()
                .thenReturn(Either.Right(fileSharingModel))
            return this
        }


        suspend fun withOperationDeniedErrorResponse(): Arrangement {
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFileSharingFeatureConfig).whenInvoked()
                .then { Either.Left(NetworkFailure.ServerMiscommunication(operationDeniedErrorResponse)) }
            return this
        }

        suspend fun withNoTeamErrorResponse(): Arrangement {
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFileSharingFeatureConfig).whenInvoked()
                .then { Either.Left(NetworkFailure.ServerMiscommunication(noTeamErrorResponse)) }
            return this
        }

        fun arrange() = this to getFileSharingStatusUseCase
    }
}
