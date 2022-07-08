package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.keypackage.KeyPackageCountDTO
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
class RefillKeyPackageUseCaseTest {

    @Test
    fun givenKeyPackageCountIs50PercentBelowLimit_ThenRequestToRefillKeyPackageIsPerformed() = runTest {
        val keyPackageCount = (KEY_PACKAGE_LIMIT * KEY_PACKAGE_THRESHOLD - 1).toInt()

        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageCount(keyPackageCount)
            .withUploadKeyPackagesSuccessful()
            .arrange()

        val actual = refillKeyPackagesUseCase()

        verify(arrangement.keyPackageRepository).coroutine {
            uploadNewKeyPackages(TestClient.CLIENT_ID, KEY_PACKAGE_LIMIT - keyPackageCount)
        }.wasInvoked(once)

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenKeyPackageCount50PercentAboveLimit_ThenNoRequestToRefillKeyPackagesIsPerformed() = runTest {
        val keyPackageCount = (KEY_PACKAGE_LIMIT * KEY_PACKAGE_THRESHOLD).toInt()

        val (_, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageCount(keyPackageCount)
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenErrorIsEncountered_ThenFailureIsPropagated() = runTest {
        val networkFailure = NetworkFailure.NoNetworkConnection(null)

        val (_, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withGetAvailableKeyPackagesFailing(networkFailure)
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(actual.failure, networkFailure)
    }

    private class Arrangement {
        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val clientRepository: ClientRepository = mock(classOf<ClientRepository>())

        private var refillKeyPackageUseCase = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository, clientRepository
        )

        fun withExistingSelfClientId() = apply {
            given(clientRepository).suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .then { Either.Right(TestClient.CLIENT_ID) }
        }

        fun withKeyPackageCount(count: Int) = apply {
            given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
                .whenInvokedWith(anything())
                .then { Either.Right(KeyPackageCountDTO(count)) }
        }

        fun withUploadKeyPackagesSuccessful() = apply {
            given(keyPackageRepository).suspendFunction(keyPackageRepository::uploadNewKeyPackages)
                .whenInvokedWith(eq(TestClient.CLIENT_ID), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetAvailableKeyPackagesFailing(failure: NetworkFailure) = apply {
            given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
                .whenInvokedWith(anything())
                .then { Either.Left(failure) }
        }

        fun arrange() = this to refillKeyPackageUseCase

    }

}
