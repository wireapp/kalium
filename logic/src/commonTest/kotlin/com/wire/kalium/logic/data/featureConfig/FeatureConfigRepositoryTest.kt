package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
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
        val (arrangement, featureConfigRepository) = Arrangement().arrange()
        arrangement.withSuccessfulResponse()

        // when
        val result = featureConfigRepository.getFileSharingFeatureConfig()

        // then
        result.shouldSucceed { arrangement.expectedSuccess.value }
        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::fileSharingFeatureConfig)
            .wasInvoked(once)
    }

    @Test
    fun whenFileSharingFeatureConfigFailWithOperationDeniedError_thenTheErrorIsPropagated() = runTest {
        // Given
        val (arrangement, featureConfigRepository) = Arrangement().arrange()
        arrangement.withOperationDeniedErrorResponse()

        // when
        val result = featureConfigRepository.getFileSharingFeatureConfig()

        // then
        result.shouldFail { arrangement.operationDeniedFailExpected.value }

        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::fileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenFileSharingFeatureConfigFailWithNoTeamError_thenTheErrorIsPropagated() = runTest {
        // Given
        val (arrangement, featureConfigRepository) = Arrangement().arrange()
        arrangement.withNoTeamErrorResponse()

        // when
        val result = featureConfigRepository.getFileSharingFeatureConfig()

        // then
        result.shouldFail { arrangement.noTeamFailExpected.value }

        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::fileSharingFeatureConfig)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {


        private val fileSharingModel = FileSharingModel(lockStatus = "locked", status = "enabled")
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

        val expectedSuccess = Either.Right(fileSharingModel)
        val operationDeniedFailExpected = Either.Left(operationDeniedErrorResponse)
        val noTeamFailExpected = Either.Left(noTeamErrorResponse)

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

        fun withOperationDeniedErrorResponse(): Arrangement {
            given(featureConfigApi)
                .suspendFunction(featureConfigApi::fileSharingFeatureConfig)
                .whenInvoked()
                .then {
                    NetworkResponse.Error(
                        operationDeniedErrorResponse
                    )
                }

            return this
        }

        fun withNoTeamErrorResponse(): Arrangement {
            given(featureConfigApi)
                .suspendFunction(featureConfigApi::fileSharingFeatureConfig)
                .whenInvoked()
                .then {
                    NetworkResponse.Error(
                        noTeamErrorResponse
                    )
                }

            return this
        }

        fun arrange() = this to featureConfigRepository
    }
}
