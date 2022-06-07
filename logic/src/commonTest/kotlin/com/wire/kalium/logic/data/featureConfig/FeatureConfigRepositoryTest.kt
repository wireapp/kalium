package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureConfigRepositoryTest {

    @Test
    fun whenFileSharingFeatureConfigSuccess_thenTheSuccessIsReturned() = runTest {
        // Given
        val expectedSuccess = Either.Right(FileSharingModel(lockStatus = "locked", status = "enabled"))
        val (arrangement, featureConfigRepository) = Arrangement().arrange()
        arrangement.withSuccessfulResponse()

        // When
        val result = featureConfigRepository.getFileSharingFeatureConfig()

        // Then
        result.shouldSucceed { expectedSuccess.value }
        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::fileSharingFeatureConfig)
            .wasInvoked(once)
    }

    @Test
    fun whenFileSharingFeatureConfigFailWithOperationDeniedError_thenTheErrorIsPropagated() = runTest {
        // Given
        val operationDeniedException = TestNetworkException.operationDenied
        val (arrangement, featureConfigRepository) = Arrangement().arrange()
        arrangement.withErrorResponse(operationDeniedException)

        // When
        val result = featureConfigRepository.getFileSharingFeatureConfig()

        // Then
        result.shouldFail { Either.Left(operationDeniedException).value }

        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::fileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenFileSharingFeatureConfigFailWithNoTeamError_thenTheErrorIsPropagated() = runTest {
        // Given
        val noTeamException = TestNetworkException.noTeam
        val (arrangement, featureConfigRepository) = Arrangement().arrange()
        arrangement.withErrorResponse(noTeamException)

        // When
        val result = featureConfigRepository.getFileSharingFeatureConfig()

        // Then
        result.shouldFail { Either.Left(noTeamException).value }

        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::fileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        val featureConfigResponse = FeatureConfigResponse("locked", "enabled")

        @Mock
        val featureConfigApi: FeatureConfigApi = mock(classOf<FeatureConfigApi>())

        var featureConfigRepository = FeatureConfigDataSource(featureConfigApi)

        fun withSuccessfulResponse(): Arrangement {
            given(featureConfigApi)
                .suspendFunction(featureConfigApi::fileSharingFeatureConfig).whenInvoked().then {
                    NetworkResponse.Success(featureConfigResponse, mapOf(), 200)
                }
            return this
        }

        fun withErrorResponse(kaliumException: KaliumException): Arrangement {
            given(featureConfigApi)
                .suspendFunction(featureConfigApi::fileSharingFeatureConfig)
                .whenInvoked()
                .then {
                    NetworkResponse.Error(
                        kaliumException
                    )
                }
            return this
        }

        fun arrange() = this to featureConfigRepository
    }
}
