package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.keypackage.KeyPackageCountDTO
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MLSKeyPackageCountUseCaseTest {
    @Mock
    private val keyPackageRepository = mock(classOf<KeyPackageRepository>())

    @Mock
    private val clientRepository: ClientRepository = mock(classOf<ClientRepository>())

    private lateinit var keyPackageCountUseCase: MLSKeyPackageCountUseCase

    @BeforeTest
    fun setup() {
        keyPackageCountUseCase = MLSKeyPackageCountUseCaseImpl(
            keyPackageRepository, clientRepository
        )
    }

    @Test
    fun givenClientIdIsNotRegistered_ThenReturnGenericError() = runTest {

        val clientFetchError = CoreFailure.MissingClientRegistration
        val mlsKeyPackageCountError = MLSKeyPackageCountResult.Failure.Generic(clientFetchError)
        given(clientRepository).function(clientRepository::currentClientId).whenInvoked()
            .then { Either.Left(clientFetchError) }

        val actual = keyPackageCountUseCase()

        verify(keyPackageRepository).coroutine { getAvailableKeyPackageCount(TestClient.CLIENT_ID) }.wasNotInvoked()
        assertIs<MLSKeyPackageCountResult.Failure.Generic>(actual)
        assertEquals(actual.genericFailure, clientFetchError)
    }


    @Test
    fun givenClientId_whenCallingKeyPackageCountReturnValue_ThenReturnKeyPackageCountSuccess() = runTest {
        given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount).whenInvokedWith(anything())
            .then { Either.Right(KEY_PACKAGE_COUNT_DTO) }

        given(clientRepository).function(clientRepository::currentClientId).whenInvoked().then { Either.Right(TestClient.CLIENT_ID) }

        val actual = keyPackageCountUseCase()

        verify(keyPackageRepository).coroutine {
            getAvailableKeyPackageCount(TestClient.CLIENT_ID)
        }.wasInvoked(once)
        assertIs<MLSKeyPackageCountResult.Success>(actual)
        assertEquals(actual, MLSKeyPackageCountResult.Success(TestClient.CLIENT_ID, KEY_PACKAGE_COUNT))
    }

    @Test
    fun givenClientID_whenCallingKeyPackageCountReturnError_ThenReturnKeyPackageCountFailure() = runTest {
        val networkFailure = NetworkFailure.NoNetworkConnection(null)

        given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount).whenInvokedWith(anything())
            .then { Either.Left(networkFailure) }

        given(clientRepository).function(clientRepository::currentClientId).whenInvoked().then { Either.Right(TestClient.CLIENT_ID) }

        val actual = keyPackageCountUseCase()

        verify(keyPackageRepository).coroutine {
            getAvailableKeyPackageCount(TestClient.CLIENT_ID)
        }.wasInvoked(once)
        assertIs<MLSKeyPackageCountResult.Failure.Generic>(actual)
        assertEquals(actual.genericFailure, networkFailure)
    }

    companion object {
        const val KEY_PACKAGE_COUNT = 10
        val KEY_PACKAGE_COUNT_DTO = KeyPackageCountDTO(KEY_PACKAGE_COUNT)
    }
}

