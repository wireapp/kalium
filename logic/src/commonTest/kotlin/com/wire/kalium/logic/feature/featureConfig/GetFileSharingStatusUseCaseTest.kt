package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FileSharingModel
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.asset.SendImageMessageResult
import com.wire.kalium.logic.feature.asset.SendImageMessageUseCaseImpl
import com.wire.kalium.logic.feature.asset.SendImageUseCaseTest
import com.wire.kalium.logic.feature.client.SelfClientsResult
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.session.PushTokenUseCaseTest
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ExperimentalCoroutinesApi
class GetFileSharingStatusUseCaseTest {


    @Test
    fun givenRepositoryCallIsSuccessful_thenSuccessIsReturned() = runTest {
        val (arrange, getFileSharingStatusUseCase) = Arrangement()
            .withSuccessfulResponse()
            .arrange()

        val actual = getFileSharingStatusUseCase.invoke()
        assertIs<GetFileSharingStatusResult.Success>(actual)
        assertEquals(arrange.fileSharingModel, actual.fileSharingModel)
        verify(arrange.featureConfigRepository)
            .coroutine { getFileSharingFeatureConfig() }
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenRepositoryCallFailWithInvalidCredentials_thenOperationDeniedIsReturned() = runTest {
        val (arrange, getFileSharingStatusUseCase) = Arrangement()
            .withErrorResponse()
            .arrange()

        val actual = getFileSharingStatusUseCase.invoke()

        assertIs<GetFileSharingStatusResult.Failure.OperationDenied>(actual)
        assertEquals(arrange.operationDeniedFail, actual)

        verify(arrange.featureConfigRepository)
            .coroutine { getFileSharingFeatureConfig() }
            .wasInvoked(exactly = once)
    }


    private class Arrangement {

        val fileSharingModel = FileSharingModel("locked", "enabled")
        val expectedFail = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)
        val operationDeniedFail = GetFileSharingStatusResult.Failure.OperationDenied

        @Mock
        val featureConfigRepository = mock(classOf<FeatureConfigRepository>())

        val getFileSharingStatusUseCase =
            GetFileSharingStatusUseCaseImpl(featureConfigRepository)

        suspend fun withSuccessfulResponse(): Arrangement {
            given(featureConfigRepository)
                .coroutine { getFileSharingFeatureConfig() }
                .then { Either.Right(fileSharingModel) }

            return this
        }

        suspend fun withErrorResponse(): Arrangement {
            given(featureConfigRepository)
                .coroutine { getFileSharingFeatureConfig() }
                .then { Either.Left(expectedFail) }

            return this
        }


        fun arrange() = this to getFileSharingStatusUseCase
    }

}
