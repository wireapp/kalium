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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RefillKeyPackageUseCaseTest {

    @Mock
    private val keyPackageRepository = mock(classOf<KeyPackageRepository>())

    @Mock
    private val clientRepository: ClientRepository = mock(classOf<ClientRepository>())

    private lateinit var refillKeyPackageUseCase: RefillKeyPackagesUseCase

    @BeforeTest
    fun setup() {
        refillKeyPackageUseCase = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository, clientRepository
        )
    }

    @Test
    fun givenKeyPackageCountIs50PercentBelowLimit_ThenRequestToRefillKeyPackageIsPerformed() = runTest {
        val keyPackageCount = (KEY_PACKAGE_LIMIT * KEY_PACKAGE_THRESHOLD - 1).toInt()

        given(clientRepository).suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .then { Either.Right(TestClient.CLIENT_ID) }
        given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
            .whenInvokedWith(anything())
            .then { Either.Right(KeyPackageCountDTO(keyPackageCount)) }
        given(keyPackageRepository).suspendFunction(keyPackageRepository::uploadNewKeyPackages)
            .whenInvokedWith(eq(TestClient.CLIENT_ID), anything())
            .thenReturn(Either.Right(Unit))

        val actual = refillKeyPackageUseCase()

        verify(keyPackageRepository).coroutine {
            uploadNewKeyPackages(TestClient.CLIENT_ID, KEY_PACKAGE_LIMIT - keyPackageCount)
        }.wasInvoked(once)

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenKeyPackageCount50PercentAboveLimit_ThenNoRequestToRefillKeyPackagesIsPerformed() = runTest {
        val keyPackageCount = (KEY_PACKAGE_LIMIT * KEY_PACKAGE_THRESHOLD).toInt()

        given(clientRepository).suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .then { Either.Right(TestClient.CLIENT_ID) }
        given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
            .whenInvokedWith(anything())
            .then { Either.Right(KeyPackageCountDTO(keyPackageCount)) }

        val actual = refillKeyPackageUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenErrorIsEncountered_ThenFailureIsPropagated() = runTest {
        val networkFailure = NetworkFailure.NoNetworkConnection(null)

        given(clientRepository).suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .then { Either.Right(TestClient.CLIENT_ID) }
        given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
            .whenInvokedWith(anything())
            .then { Either.Left(networkFailure) }

        val actual = refillKeyPackageUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(actual.failure, networkFailure)
    }

}
