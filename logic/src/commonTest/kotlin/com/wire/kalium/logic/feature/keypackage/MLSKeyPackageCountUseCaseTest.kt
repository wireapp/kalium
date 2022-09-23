package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.CLIENT_FETCH_ERROR
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.KEY_PACKAGE_COUNT
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.KEY_PACKAGE_COUNT_DTO
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.NETWORK_FAILURE
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import io.mockative.Mock
import io.mockative.anything
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

@OptIn(ExperimentalCoroutinesApi::class)
class MLSKeyPackageCountUseCaseTest {

    @Test
    fun givenClientIdIsNotRegistered_ThenReturnGenericError() = runTest {
        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withClientId(Either.Left(CLIENT_FETCH_ERROR))
            .arrange()

        val actual = keyPackageCountUseCase()

        verify(arrangement.keyPackageRepository)
            .suspendFunction(arrangement.keyPackageRepository::getAvailableKeyPackageCount)
            .with(eq(TestClient.CLIENT_ID))
            .wasNotInvoked()

        assertIs<MLSKeyPackageCountResult.Failure.FetchClientIdFailure>(actual)
        assertEquals(actual.genericFailure, CLIENT_FETCH_ERROR)
    }

    @Test
    fun givenClientId_whenCallingKeyPackageCountReturnValue_ThenReturnKeyPackageCountSuccess() = runTest {
        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withAvailableKeyPackageCountReturn(Either.Right(KEY_PACKAGE_COUNT_DTO))
            .withClientId(Either.Right(TestClient.CLIENT_ID))
            .withKeyPackageLimitSucceed()
            .arrange()

        val actual = keyPackageCountUseCase()

        verify(arrangement.keyPackageRepository)
            .suspendFunction(arrangement.keyPackageRepository::getAvailableKeyPackageCount)
            .with(eq(TestClient.CLIENT_ID))
            .wasInvoked(once)
        assertIs<MLSKeyPackageCountResult.Success>(actual)
        assertEquals(actual, MLSKeyPackageCountResult.Success(TestClient.CLIENT_ID, KEY_PACKAGE_COUNT, true))
    }

    @Test
    fun givenClientID_whenCallingKeyPackageCountReturnError_ThenReturnKeyPackageCountFailure() = runTest {
        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withAvailableKeyPackageCountReturn(Either.Left(NETWORK_FAILURE))
            .withClientId(Either.Right(TestClient.CLIENT_ID))
            .arrange()

        val actual = keyPackageCountUseCase()

        verify(arrangement.keyPackageRepository)
            .suspendFunction(arrangement.keyPackageRepository::getAvailableKeyPackageCount)
            .with(eq(TestClient.CLIENT_ID))
            .wasInvoked(once)
        assertIs<MLSKeyPackageCountResult.Failure.NetworkCallFailure>(actual)
        assertEquals(actual.networkFailure, NETWORK_FAILURE)
    }

    private class Arrangement {
        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val clientRepository: ClientRepository = mock(classOf<ClientRepository>())

        @Mock
        val keyPackageLimitsProvider = mock(classOf<KeyPackageLimitsProvider>())

        fun withClientId(result: Either<CoreFailure, ClientId>) = apply {
            given(clientRepository).suspendFunction(clientRepository::currentClientId).whenInvoked()
                .then { result }
        }

        fun withKeyPackageLimitSucceed() = apply {
            given(keyPackageLimitsProvider)
                .function(keyPackageLimitsProvider::needsRefill)
                .whenInvokedWith(anything())
                .thenReturn(true)
        }

        fun withAvailableKeyPackageCountReturn(result: Either<NetworkFailure, KeyPackageCountDTO>) = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
                .whenInvokedWith(anything())
                .then { result }
        }

        fun arrange() = this to MLSKeyPackageCountUseCaseImpl(
            keyPackageRepository, clientRepository, keyPackageLimitsProvider
        )

        companion object {
            val NETWORK_FAILURE = NetworkFailure.NoNetworkConnection(null)

            val CLIENT_FETCH_ERROR = CoreFailure.MissingClientRegistration
            const val KEY_PACKAGE_COUNT = 10
            val KEY_PACKAGE_COUNT_DTO = KeyPackageCountDTO(KEY_PACKAGE_COUNT)
        }
    }
}
