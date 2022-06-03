package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
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
        val arrangement = Arrangement()
        arrangement.withSuccessfulResponse()

        val actual = arrangement.featureConfigRepository.getFileSharingFeatureConfig()

        actual.shouldSucceed { arrangement.expectedSuccess.value }
        verify(arrangement.featureConfigRepository)
            .coroutine { arrangement.featureConfigRepository.getFileSharingFeatureConfig() }
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenFileSharingFeatureConfigFail_thenTheErrorIsPropagated() = runTest {
        val arrangement = Arrangement()
        arrangement.withErrorResponse()

        val actual = arrangement.featureConfigRepository.getFileSharingFeatureConfig()

        actual.shouldFail { arrangement.failExpected.value }

        verify(arrangement.featureConfigRepository)
            .coroutine { arrangement.featureConfigRepository.getFileSharingFeatureConfig() }
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        private val fileSharingModel = FileSharingModel(lockStatus = "locked", status = "enabled")
        private val errorResponse = NetworkFailure.ServerMiscommunication(
            KaliumException.InvalidRequestError(
                ErrorResponse(
                    403, "Insufficient permissions", "operation-denied"
                )
            )
        )

        val expectedSuccess = Either.Right(fileSharingModel)
        val failExpected = Either.Left(errorResponse)

        @Mock
        val featureConfigRepository = mock(classOf<FeatureConfigRepository>())


        suspend fun withSuccessfulResponse(): Arrangement {
            given(featureConfigRepository)
                .coroutine { featureConfigRepository.getFileSharingFeatureConfig() }
                .then { expectedSuccess }
            return this
        }

        suspend fun withErrorResponse(): Arrangement {
            given(featureConfigRepository)
                .coroutine { featureConfigRepository.getFileSharingFeatureConfig() }
                .then { failExpected }
            return this
        }
    }
}
