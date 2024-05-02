/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseTest.Arrangement.Companion.E2EI_TEAM_SETTINGS
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class RegisterMLSClientUseCaseTest {
    @Test
    fun givenRegisterMLSClientUseCaseAndE2EIIsRequired_whenInvokedAndE2EIIsEnrolled_thenRegisterMLSClient() =
        runTest {
            val e2eiIsRequired = true
            val e2eiIsEnrolled = true
            val (arrangement, registerMLSClient) = Arrangement()
                .withGetMLSClientSuccessful()
                .withIsMLSClientInitialisedReturns()
                .withMLSClientE2EIIsEnabledReturns(e2eiIsEnrolled)
                .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(isRequired = e2eiIsRequired)))
                .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY, Arrangement.MLS_CIPHER_SUITE)
                .withRegisterMLSClient(Either.Right(Unit))
                .withKeyPackageLimits(Arrangement.REFILL_AMOUNT)
                .withUploadKeyPackagesSuccessful()
                .arrange()

            val result = registerMLSClient(TestClient.CLIENT_ID)

            result.shouldSucceed()

            assertIs<RegisterMLSClientResult.Success>(result.value)

            coVerify {
                arrangement.clientRepository.registerMLSClient(eq(TestClient.CLIENT_ID), eq(Arrangement.MLS_PUBLIC_KEY))
            }.wasInvoked(exactly = once)

            coVerify {

                arrangement.keyPackageRepository.uploadNewKeyPackages(eq(TestClient.CLIENT_ID), eq(Arrangement.REFILL_AMOUNT))

            }
        }

    @Test
    fun givenRegisterMLSClientUseCaseAndE2EIIsRequired_whenInvokedAndE2EIIsNotEnrolled_thenNotRegisterMLSClient() =
        runTest {
            val e2eiIsRequired = true
            val e2eiIsEnrolled = false
            val (arrangement, registerMLSClient) = Arrangement()
                .withGetMLSClientSuccessful()
                .withIsMLSClientInitialisedReturns(false)
                .withMLSClientE2EIIsEnabledReturns(e2eiIsEnrolled)
                .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(isRequired = e2eiIsRequired)))
                .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY, Arrangement.MLS_CIPHER_SUITE)
                .withRegisterMLSClient(Either.Right(Unit))
                .withKeyPackageLimits(Arrangement.REFILL_AMOUNT)
                .withUploadKeyPackagesSuccessful()
                .arrange()

            val result = registerMLSClient(TestClient.CLIENT_ID)

            result.shouldSucceed()

            assertIs<RegisterMLSClientResult.E2EICertificateRequired>(result.value)

            coVerify {
                arrangement.clientRepository.registerMLSClient(eq(TestClient.CLIENT_ID), eq(Arrangement.MLS_PUBLIC_KEY))
            }.wasNotInvoked()

            coVerify {
                arrangement.keyPackageRepository.uploadNewKeyPackages(eq(TestClient.CLIENT_ID), eq(Arrangement.REFILL_AMOUNT))
            }.wasNotInvoked()
        }

    @Test
    fun givenRegisterMLSClientUseCaseAndE2EIIsNotRequired_whenInvoked_thenRegisterMLSClient() =
        runTest {
            val e2eiIsRequired = false
            val (arrangement, registerMLSClient) = Arrangement()
                .withGetMLSClientSuccessful()
                .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(isRequired = e2eiIsRequired)))
                .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY, Arrangement.MLS_CIPHER_SUITE)
                .withRegisterMLSClient(Either.Right(Unit))
                .withKeyPackageLimits(Arrangement.REFILL_AMOUNT)
                .withUploadKeyPackagesSuccessful()
                .arrange()

            val result = registerMLSClient(TestClient.CLIENT_ID)

            result.shouldSucceed()

            assertIs<RegisterMLSClientResult.Success>(result.value)

            coVerify {
                arrangement.clientRepository.registerMLSClient(eq(TestClient.CLIENT_ID), eq(Arrangement.MLS_PUBLIC_KEY))
            }.wasInvoked(exactly = once)

            coVerify {

                arrangement.keyPackageRepository.uploadNewKeyPackages(eq(TestClient.CLIENT_ID), eq(Arrangement.REFILL_AMOUNT))

            }
        }

    private class Arrangement {

        @Mock
        val mlsClient = mock(MLSClient::class)

        @Mock
        var mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val keyPackageRepository = mock(KeyPackageRepository::class)

        @Mock
        val keyPackageLimitsProvider = mock(KeyPackageLimitsProvider::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        fun withGettingE2EISettingsReturns(result: Either<StorageFailure, E2EISettings>) = apply {
            every {
                userConfigRepository.getE2EISettings()
            }.returns(result)
        }

        suspend fun withMLSClientE2EIIsEnabledReturns(result: Boolean) = apply {
            coEvery {
                mlsClient.isE2EIEnabled()
            }.returns(result)
        }

        fun withIsMLSClientInitialisedReturns(result: Boolean = true) = apply {
            every {
                mlsClientProvider.isMLSClientInitialised()
            }.returns(result)
        }

        suspend fun withRegisterMLSClient(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                clientRepository.registerMLSClient(any(), any())
            }.returns(result)
        }

        fun withKeyPackageLimits(refillAmount: Int) = apply {
            every {
                keyPackageLimitsProvider.refillAmount()
            }.returns(refillAmount)
        }

        suspend fun withUploadKeyPackagesSuccessful() = apply {
            coEvery {
                keyPackageRepository.uploadNewKeyPackages(eq(TestClient.CLIENT_ID), any())
            }.returns(Either.Right(Unit))
        }

<<<<<<< HEAD
        suspend fun withGetPublicKey(result: ByteArray) = apply {
            coEvery {
                mlsClient.getPublicKey()
            }.returns(result)
=======
        fun withGetPublicKey(publicKey: ByteArray, cipherSuite: UShort) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::getPublicKey)
                .whenInvoked()
                .thenReturn(publicKey to cipherSuite)
>>>>>>> 00d9937da6 (feat: pass the MLS public key signature algorithm when updating MLS p… (#2720))
        }

        suspend fun withGetMLSClientSuccessful() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))
        }

        fun arrange() = this to RegisterMLSClientUseCaseImpl(
            mlsClientProvider,
            clientRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            userConfigRepository
        )

        companion object {
            val MLS_PUBLIC_KEY = "public_key".encodeToByteArray()
            val MLS_CIPHER_SUITE = 0.toUShort()
            const val REFILL_AMOUNT = 100
            val RANDOM_URL = "https://random.rn"
            val E2EI_TEAM_SETTINGS = E2EISettings(
                true, RANDOM_URL, DateTimeUtil.currentInstant()
            )
        }

    }
}
