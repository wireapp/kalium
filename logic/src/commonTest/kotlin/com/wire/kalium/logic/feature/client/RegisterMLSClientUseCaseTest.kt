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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test

class RegisterMLSClientUseCaseTest {
    //todo: fix later
    @Ignore
    @Test
    fun givenRegisterMLSClientUseCase_whenInvoked_thenRegisterMLSClient() =
        runTest() {
            val (arrangement, registerMLSClient) = Arrangement()
                .withGetMLSClientSuccessful()
                .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY)
                .withRegisterMLSClient(Either.Right(Unit))
                .withKeyPackageLimits(Arrangement.REFILL_AMOUNT)
                .withUploadKeyPackagesSuccessful()
                .arrange()

            registerMLSClient(TestClient.CLIENT_ID)

            verify(arrangement.clientRepository)
                .suspendFunction(arrangement.clientRepository::registerMLSClient)
                .with(eq(TestClient.CLIENT_ID), eq(Arrangement.MLS_PUBLIC_KEY))
                .wasInvoked(exactly = once)

            verify(arrangement.keyPackageRepository)
                .suspendFunction(arrangement.keyPackageRepository::uploadNewKeyPackages)
                .with(eq(TestClient.CLIENT_ID), eq(Arrangement.REFILL_AMOUNT))
        }

    private class Arrangement {

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        var mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val keyPackageLimitsProvider = mock(classOf<KeyPackageLimitsProvider>())
        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun withRegisterMLSClient(result: Either<CoreFailure, Unit>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::registerMLSClient)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withKeyPackageLimits(refillAmount: Int) = apply {
            given(keyPackageLimitsProvider).function(keyPackageLimitsProvider::refillAmount)
                .whenInvoked()
                .thenReturn(refillAmount)
        }

        fun withUploadKeyPackagesSuccessful() = apply {
            given(keyPackageRepository).suspendFunction(keyPackageRepository::uploadNewKeyPackages)
                .whenInvokedWith(eq(TestClient.CLIENT_ID), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetPublicKey(result: ByteArray) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::getPublicKey)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withGetMLSClientSuccessful() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(mlsClient) }
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
            const val REFILL_AMOUNT = 100
        }

    }
}
